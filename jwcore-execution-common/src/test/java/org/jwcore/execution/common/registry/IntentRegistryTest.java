package org.jwcore.execution.common.registry;

import org.jwcore.core.time.ControllableTimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventType;
import org.jwcore.domain.RejectReason;
import org.jwcore.execution.common.emit.EventEmitter;
import org.jwcore.execution.common.runtime.PendingIntent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IntentRegistryTest {
    @Test
    void shouldBindLookupRemoveAndEmitTimeout() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var registry = new IntentRegistry(time, new EventEmitter(journal, time));
        final UUID intentId = UUID.randomUUID();
        final CanonicalId canonicalId = CanonicalId.parse("S07:I03:VA07-03:BA01");
        registry.bind(intentId, canonicalId, "crypto", time.eventTime(), 1000L);

        assertEquals(canonicalId, registry.findCanonicalId(intentId).orElseThrow());
        assertEquals(intentId, registry.findIntentId(canonicalId).orElseThrow());

        time.advanceBy(Duration.ofSeconds(2));
        registry.checkTimeouts(30_000L);
        assertTrue(registry.findCanonicalId(intentId).isEmpty());
        assertEquals(1, journal.all().size());

        registry.bind(intentId, canonicalId, "crypto", time.eventTime(), 1000L);
        assertTrue(registry.remove(intentId));
        assertTrue(registry.findIntentId(canonicalId).isEmpty());
    }

    @Test
    void shouldMarkCorrelationIdAsTerminated() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var registry = new IntentRegistry(time, new EventEmitter(journal, time));
        final UUID correlationId = UUID.randomUUID();

        registry.markTerminated(correlationId);

        assertTrue(registry.isTerminated(correlationId));
    }

    @Test
    void shouldReturnFalseForNonTerminatedCorrelationId() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var registry = new IntentRegistry(time, new EventEmitter(journal, time));

        assertFalse(registry.isTerminated(UUID.randomUUID()));
    }

    @Test
    void shouldAbsorbTerminalEventsFromJournal() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var emitter = new EventEmitter(journal, time, "exec-test-node");
        final var registry = new IntentRegistry(time, emitter);

        final UUID terminatedCorrelationId = UUID.randomUUID();
        final PendingIntent terminatedPending = new PendingIntent(
                terminatedCorrelationId,
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "crypto",
                time.eventTime(),
                1000L
        );
        final var rejected = emitter.emitOrderRejected(terminatedPending, RejectReason.SAFE_STATE).envelope();

        final UUID nonTerminalCorrelationId = UUID.randomUUID();
        final byte[] payload = "crypto-account|BTCUSDT|0.10".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final var orderIntent = emitter.createEnvelope(
                EventType.OrderIntentEvent,
                null,
                nonTerminalCorrelationId.toString(),
                CanonicalId.parse("S07:I04:VA07-04:BA01"),
                payload,
                nonTerminalCorrelationId
        );

        registry.absorb(List.of(orderIntent, rejected));

        assertTrue(registry.isTerminated(terminatedCorrelationId));
        assertFalse(registry.isTerminated(nonTerminalCorrelationId));
    }

    @Test
    void shouldMarkIntentAsSubmittedOnOrderSubmittedEvent() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var emitter = new EventEmitter(journal, time, "exec-test-node");
        final var registry = new IntentRegistry(time, emitter);
        final CanonicalId canonicalId = CanonicalId.parse("S07:I03:VA07-03:BA01");

        final UUID correlationId = UUID.randomUUID();
        registry.bind(correlationId, canonicalId, "crypto", time.eventTime(), 1000L);
        final PendingIntent pendingIntent = new PendingIntent(correlationId, canonicalId, "crypto", time.eventTime(), 1000L);
        final var submittedEvent = emitter.emitOrderSubmitted(pendingIntent, "BROKER-ORD-11", 0.25d).envelope();

        registry.absorb(List.of(submittedEvent));

        assertEquals(IntentPhase.SUBMITTED, registry.getPhase(correlationId).orElseThrow());
        assertFalse(registry.isTerminated(correlationId));
    }

    @Test
    void shouldMarkIntentAsTerminatedOnOrderFilledEvent() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var emitter = new EventEmitter(journal, time, "exec-test-node");
        final var registry = new IntentRegistry(time, emitter);
        final UUID intentId = UUID.randomUUID();
        registry.bind(intentId, CanonicalId.parse("S07:I03:VA07-03:BA01"), "crypto", time.eventTime(), 1000L);

        final var filled = emitter.createEnvelope(
                EventType.OrderFilledEvent,
                "BROKER-1",
                intentId.toString(),
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "filled".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                intentId
        );

        registry.absorb(List.of(filled));

        assertTrue(registry.isTerminated(intentId));
        assertTrue(registry.getPhase(intentId).isEmpty());
    }

    @Test
    void shouldMarkIntentAsTerminatedOnOrderUnknownEvent() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var emitter = new EventEmitter(journal, time, "exec-test-node");
        final var registry = new IntentRegistry(time, emitter);
        final UUID intentId = UUID.randomUUID();
        final CanonicalId canonicalId = CanonicalId.parse("S07:I03:VA07-03:BA01");
        registry.bind(intentId, canonicalId, "crypto", time.eventTime(), 1000L);
        final PendingIntent pendingIntent = new PendingIntent(intentId, canonicalId, "crypto", time.eventTime(), 1000L);

        final var unknown = emitter.emitOrderUnknown(pendingIntent, "BROKER_TIMEOUT").envelope();
        registry.absorb(List.of(unknown));

        assertTrue(registry.isTerminated(intentId));
        assertTrue(registry.getPhase(intentId).isEmpty());
    }

    @Test
    void shouldKeepSubmittedIntentUntilFinalEvent() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var emitter = new EventEmitter(journal, time, "exec-test-node");
        final var registry = new IntentRegistry(time, emitter);
        final UUID intentId = UUID.randomUUID();
        final CanonicalId canonicalId = CanonicalId.parse("S07:I03:VA07-03:BA01");
        registry.bind(intentId, canonicalId, "crypto", time.eventTime(), 1000L);
        final PendingIntent pendingIntent = new PendingIntent(intentId, canonicalId, "crypto", time.eventTime(), 1000L);

        final var submitted = emitter.emitOrderSubmitted(pendingIntent, "BROKER-ORD-22", 0.50d).envelope();
        registry.absorb(List.of(submitted));
        assertEquals(IntentPhase.SUBMITTED, registry.getPhase(intentId).orElseThrow());
        assertEquals(1, registry.countInPhase(IntentPhase.SUBMITTED));

        final var filled = emitter.createEnvelope(
                EventType.OrderFilledEvent,
                "BROKER-ORD-22",
                intentId.toString(),
                canonicalId,
                "filled".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                intentId
        );
        registry.absorb(List.of(filled));
        assertTrue(registry.getPhase(intentId).isEmpty());
        assertEquals(0, registry.countInPhase(IntentPhase.SUBMITTED));
    }
}
