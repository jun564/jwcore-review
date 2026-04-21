package org.jwcore.execution.common.emit;

import org.jwcore.core.time.ControllableTimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventType;
import org.jwcore.domain.RejectReason;
import org.jwcore.domain.events.OrderRejectedEvent;
import org.jwcore.domain.events.OrderUnknownEvent;
import org.jwcore.execution.common.events.Discrepancy;
import org.jwcore.execution.common.events.RebuildType;
import org.jwcore.execution.common.runtime.PendingIntent;
import org.jwcore.execution.common.state.ExecutionState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventEmitterTest {
    @Test
    void shouldEmitAllSupportedEventTypesAndParseRiskDecision() {
        final var journal = new org.jwcore.execution.common.registry.InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var emitter = new EventEmitter(journal, time, "exec-crypto-1");

        final var risk = emitter.createRiskDecisionEvent("crypto", ExecutionState.HALT, "risk breach");
        emitter.emit(risk.envelope());
        assertEquals(EventType.RiskDecisionEvent, journal.all().get(0).eventType());
        assertEquals(ExecutionState.HALT, emitter.parseRiskDecisionEvent(risk.envelope()).desiredState());
        assertEquals("exec-crypto-1", risk.envelope().sourceProcessId());
        assertNull(risk.envelope().correlationId());
        assertEquals("risk-decision:crypto:HALT:risk breach", risk.envelope().idempotencyKey());

        final var margin = emitter.createMarginUpdateEvent("crypto", new BigDecimal("50"), new BigDecimal("1000"), new BigDecimal("1200"));
        emitter.emit(margin.envelope());
        assertEquals(EventType.MarginUpdateEvent, journal.all().get(1).eventType());
        assertEquals(margin.envelope().eventId(), margin.envelope().correlationId());

        final UUID intentId = UUID.randomUUID();
        final var pendingIntent = new PendingIntent(intentId, CanonicalId.parse("S07:I03:VA07-03:BA01"), "crypto", time.eventTime(), 5000L);
        final var timeout = emitter.createOrderTimeoutEvent(pendingIntent);
        emitter.emit(timeout.envelope());
        assertEquals(EventType.OrderTimeoutEvent, journal.all().get(2).eventType());
        assertEquals(intentId, timeout.envelope().correlationId());

        final var submitted = emitter.emitOrderSubmitted(pendingIntent, "BROKER-ORD-1", 0.75d);
        assertEquals(EventType.OrderSubmittedEvent, journal.all().get(3).eventType());
        assertEquals(intentId, submitted.envelope().correlationId());
        assertEquals("exec-crypto-1", submitted.envelope().sourceProcessId());
        assertArrayEquals(submitted.toPayload(), submitted.envelope().payload());

        final var unknown = emitter.emitOrderUnknown(pendingIntent, "BROKER_TIMEOUT");
        assertEquals(EventType.OrderUnknownEvent, journal.all().get(4).eventType());
        assertEquals(intentId, unknown.envelope().correlationId());
        assertEquals("BROKER_TIMEOUT", unknown.reason());
        assertEquals("crypto", unknown.accountId());
        assertEquals("crypto", OrderUnknownEvent.fromPayload(unknown.envelope().payload()).accountId());

        final var rejected = emitter.emitOrderRejected(pendingIntent, RejectReason.RISK_LIMIT);
        assertEquals(EventType.OrderRejectedEvent, journal.all().get(5).eventType());
        assertEquals("exec-crypto-1", rejected.envelope().sourceProcessId());
        assertEquals(intentId, rejected.envelope().correlationId());
        assertEquals(RejectReason.RISK_LIMIT, rejected.reason());
        assertEquals("crypto", rejected.accountId());
        assertEquals("crypto", OrderRejectedEvent.fromPayload(rejected.envelope().payload()).accountId());

        final var rebuilt = emitter.createStateRebuiltEvent(
                "crypto",
                1,
                UUID.randomUUID(),
                time.eventTime(),
                RebuildType.CLEAN,
                5,
                List.of(new Discrepancy("desc", "expected", "actual", time.eventTime()))
        );
        emitter.emit(rebuilt.envelope());
        assertEquals(EventType.StateRebuiltEvent, journal.all().get(6).eventType());
        assertEquals(7, journal.all().size());
        assertNotNull(rebuilt.envelope().correlationId());
    }

    @Test
    void shouldThrowWhenEmitEventProcessingFailedCalledWithNullException() {
        final var journal = new org.jwcore.execution.common.registry.InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var emitter = new EventEmitter(journal, time, "exec-crypto-1");

        assertThrows(NullPointerException.class, () -> emitter.emitEventProcessingFailed(UUID.randomUUID(), null));
    }

    @Test
    void shouldTruncateLongErrorMessageForEventProcessingFailed() {
        final var journal = new org.jwcore.execution.common.registry.InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var emitter = new EventEmitter(journal, time, "exec-crypto-1");
        final String longMessage = "x".repeat(700);

        final var failedEvent = emitter.emitEventProcessingFailed(
                UUID.randomUUID(),
                new IllegalStateException(longMessage)
        );

        assertEquals(512, failedEvent.errorMessage().length());
        assertEquals(EventType.EventProcessingFailedEvent, failedEvent.envelope().eventType());
        assertNull(failedEvent.envelope().correlationId());
        assertEquals("exec-crypto-1", failedEvent.envelope().sourceProcessId());
    }

    @Test
    void shouldThrowWhenEmitOrderSubmittedCalledWithNullBrokerOrderId() {
        final var journal = new org.jwcore.execution.common.registry.InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var emitter = new EventEmitter(journal, time, "exec-crypto-1");
        final var intent = new PendingIntent(UUID.randomUUID(), CanonicalId.parse("S07:I03:VA07-03:BA01"), "crypto", time.eventTime(), 5000L);

        assertThrows(NullPointerException.class, () -> emitter.emitOrderSubmitted(intent, null, 0.50d));
    }

    @Test
    void shouldThrowWhenEmitOrderSubmittedCalledWithBlankBrokerOrderId() {
        final var journal = new org.jwcore.execution.common.registry.InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var emitter = new EventEmitter(journal, time, "exec-crypto-1");
        final var intent = new PendingIntent(UUID.randomUUID(), CanonicalId.parse("S07:I03:VA07-03:BA01"), "crypto", time.eventTime(), 5000L);

        assertThrows(IllegalArgumentException.class, () -> emitter.emitOrderSubmitted(intent, "   ", 0.50d));
    }

    @Test
    void shouldThrowWhenEmitOrderSubmittedCalledWithNonPositiveSize() {
        final var journal = new org.jwcore.execution.common.registry.InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var emitter = new EventEmitter(journal, time, "exec-crypto-1");
        final var intent = new PendingIntent(UUID.randomUUID(), CanonicalId.parse("S07:I03:VA07-03:BA01"), "crypto", time.eventTime(), 5000L);

        assertThrows(IllegalArgumentException.class, () -> emitter.emitOrderSubmitted(intent, "BROKER-1", 0.0d));
    }
}
