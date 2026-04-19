package org.jwcore.execution.common.emit;

import org.jwcore.core.time.ControllableTimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventType;
import org.jwcore.domain.RejectReason;
import org.jwcore.execution.common.events.Discrepancy;
import org.jwcore.execution.common.events.RebuildType;
import org.jwcore.execution.common.runtime.PendingIntent;
import org.jwcore.execution.common.state.ExecutionState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        assertEquals(risk.envelope().eventId(), risk.envelope().correlationId());

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

        final var rejected = emitter.emitOrderRejected(pendingIntent, RejectReason.RISK_LIMIT);
        assertEquals(EventType.OrderRejectedEvent, journal.all().get(3).eventType());
        assertEquals("exec-crypto-1", rejected.envelope().sourceProcessId());
        assertEquals(intentId, rejected.envelope().correlationId());
        assertEquals(RejectReason.RISK_LIMIT, rejected.reason());

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
        assertEquals(EventType.StateRebuiltEvent, journal.all().get(4).eventType());
        assertEquals(5, journal.all().size());
        assertNotNull(rebuilt.envelope().correlationId());
    }
}
