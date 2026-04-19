package org.jwcore.execution.crypto.runtime;

import org.jwcore.core.time.ControllableTimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.jwcore.execution.common.emit.EventEmitter;
import org.jwcore.execution.common.state.ExecutionState;
import org.jwcore.execution.crypto.broker.StubBrokerSession;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionRuntimeTest {
    @Test
    void shouldProcessOrderIntentInRunStateAndEmitTimeoutAndMargin() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 2, 100_000);

        journal.append(orderIntentEvent(time, UUID.randomUUID(), "BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));

        runtime.tickCycle();
        assertEquals(1, brokerSession.submitted().size());
        assertEquals(1, runtime.pendingIntents());

        time.advanceBy(Duration.ofSeconds(5));
        runtime.tickCycle();
        assertTrue(journal.all().stream().anyMatch(e -> e.eventType() == EventType.OrderTimeoutEvent));
        assertTrue(journal.all().stream().anyMatch(e -> e.eventType() == EventType.MarginUpdateEvent));
    }

    @Test
    void shouldHonorMoreRestrictiveGlobalRiskDecision() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 5, 100_000);
        final EventEmitter emitter = new EventEmitter(journal, time);
        journal.append(emitter.createRiskDecisionEvent("crypto", ExecutionState.HALT, "test-halt").envelope());

        runtime.tickCycle();
        assertEquals(ExecutionState.HALT, runtime.currentState());
    }

    @Test
    void shouldIgnoreOrderIntentInSafeStateButStillTriggerTimeouts() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.SAFE, 5, 10, 100_000);

        final UUID rejectedIntentId = UUID.randomUUID();
        journal.append(orderIntentEvent(time, rejectedIntentId, "BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));
        runtime.tickCycle();

        assertEquals(0, brokerSession.submitted().size());
        assertEquals(ExecutionState.SAFE, runtime.currentState());
        assertEquals(0, runtime.pendingIntents());

        final var rejectedEvents = journal.all().stream()
                .filter(e -> e.eventType() == EventType.OrderRejectedEvent)
                .toList();
        assertEquals(1, rejectedEvents.size());
        final EventEnvelope rejected = rejectedEvents.get(0);
        assertEquals("SAFE_STATE", extractRejectReasonCode(rejected));
        assertEquals(rejectedIntentId, rejected.correlationId());
        assertEquals("crypto-execution-node-test", rejected.sourceProcessId());

        final UUID pendingIntent = UUID.randomUUID();
        journal.append(orderIntentEvent(time, pendingIntent, "ETHUSD|0.20", "S07:I04:VA07-04:BA01"));
        final var runRuntime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 10, 100_000);
        runRuntime.tickCycle();
        assertEquals(1, runRuntime.pendingIntents());

        time.advanceBy(Duration.ofSeconds(5));
        runRuntime.tickCycle();
        assertTrue(journal.all().stream().anyMatch(e -> e.eventType() == EventType.OrderTimeoutEvent));
    }

    @Test
    void shouldRejectOrderIntentInHaltStateWithEvent() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.HALT, 5, 10, 100_000);

        final UUID rejectedIntentId = UUID.randomUUID();
        journal.append(orderIntentEvent(time, rejectedIntentId, "BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));
        runtime.tickCycle();

        assertEquals(0, brokerSession.submitted().size());
        assertEquals(ExecutionState.HALT, runtime.currentState());
        assertEquals(0, runtime.pendingIntents());

        final var rejectedEvents = journal.all().stream()
                .filter(e -> e.eventType() == EventType.OrderRejectedEvent)
                .toList();
        assertEquals(1, rejectedEvents.size());
        final EventEnvelope rejected = rejectedEvents.get(0);
        assertEquals("HALT_STATE", extractRejectReasonCode(rejected));
        assertEquals(rejectedIntentId, rejected.correlationId());
        assertEquals("crypto-execution-node-test", rejected.sourceProcessId());

        final UUID pendingIntent = UUID.randomUUID();
        journal.append(orderIntentEvent(time, pendingIntent, "ETHUSD|0.20", "S07:I04:VA07-04:BA01"));
        final var runRuntime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 10, 100_000);
        runRuntime.tickCycle();
        assertEquals(1, runRuntime.pendingIntents());

        time.advanceBy(Duration.ofSeconds(5));
        runRuntime.tickCycle();
        assertTrue(journal.all().stream().anyMatch(e -> e.eventType() == EventType.OrderTimeoutEvent));
    }

    @Test
    void shouldStayInKillStateAndIgnoreOrderIntents() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final EventEmitter emitter = new EventEmitter(journal, time);
        journal.append(emitter.createRiskDecisionEvent("crypto", ExecutionState.KILL, "operator kill").envelope());
        journal.append(orderIntentEvent(time, UUID.randomUUID(), "BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));

        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 10, 100_000);
        runtime.tickCycle();

        assertEquals(ExecutionState.KILL, runtime.currentState());
        assertEquals(0, brokerSession.submitted().size());
    }

    @Test
    void shouldFollowRunHaltSafeRunSequenceAcrossRiskDecisions() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 10, 100_000);
        final EventEmitter emitter = new EventEmitter(journal, time);

        journal.append(emitter.createRiskDecisionEvent("crypto", ExecutionState.HALT, "halt").envelope());
        runtime.tickCycle();
        assertEquals(ExecutionState.HALT, runtime.currentState());

        journal.append(emitter.createRiskDecisionEvent("crypto", ExecutionState.SAFE, "step down").envelope());
        time.advanceBy(Duration.ofMillis(1));
        runtime.tickCycle();
        assertEquals(ExecutionState.SAFE, runtime.currentState());

        journal.append(emitter.createRiskDecisionEvent("crypto", ExecutionState.RUN, "resume").envelope());
        time.advanceBy(Duration.ofMillis(1));
        runtime.tickCycle();
        assertEquals(ExecutionState.RUN, runtime.currentState());
    }

    @Test
    void shouldHandleEmptyCycleAndEmitMarginOnlyOnConfiguredInterval() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 3, 100_000);

        runtime.tickCycle();
        assertTrue(journal.all().stream().noneMatch(e -> e.eventType() == EventType.MarginUpdateEvent));
        time.advanceBy(Duration.ofMillis(1));
        runtime.tickCycle();
        assertTrue(journal.all().stream().noneMatch(e -> e.eventType() == EventType.MarginUpdateEvent));
        time.advanceBy(Duration.ofMillis(1));
        runtime.tickCycle();
        assertEquals(1, journal.all().stream().filter(e -> e.eventType() == EventType.MarginUpdateEvent).count());
    }

    @Test
    void shouldEmitThreeTimeoutsInSingleCycleWithoutFailure() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 10, 100_000);

        journal.append(orderIntentEvent(time, UUID.randomUUID(), "BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));
        journal.append(orderIntentEvent(time, UUID.randomUUID(), "ETHUSD|0.20", "S07:I04:VA07-04:BA01"));
        journal.append(orderIntentEvent(time, UUID.randomUUID(), "XAUUSD|0.30", "S07:I05:VA07-05:BA01"));
        runtime.tickCycle();

        time.advanceBy(Duration.ofSeconds(5));
        runtime.tickCycle();

        assertEquals(3, journal.all().stream().filter(e -> e.eventType() == EventType.OrderTimeoutEvent).count());
        assertEquals(0, runtime.pendingIntents());
    }

    @Test
    void shouldBoundProcessedEventIdsUsingConfiguredCapacity() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 100, 2);

        journal.append(orderIntentEvent(time, UUID.randomUUID(), "BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));
        runtime.tickCycle();
        time.advanceBy(Duration.ofMillis(1));
        journal.append(orderIntentEvent(time, UUID.randomUUID(), "ETHUSD|0.20", "S07:I04:VA07-04:BA01"));
        runtime.tickCycle();
        time.advanceBy(Duration.ofMillis(1));
        journal.append(orderIntentEvent(time, UUID.randomUUID(), "XAUUSD|0.30", "S07:I05:VA07-05:BA01"));
        runtime.tickCycle();

        assertEquals(2, runtime.processedEvents());
    }

    private static String extractRejectReasonCode(final EventEnvelope envelope) {
        return new String(envelope.payload(), StandardCharsets.UTF_8).split("\\|", 3)[1];
    }

    private static ExecutionRuntime runtime(final InMemoryEventJournal journal,
                                            final ControllableTimeProvider time,
                                            final StubBrokerSession brokerSession,
                                            final org.jwcore.execution.crypto.risk.LocalRiskPolicy policy,
                                            final long timeoutSeconds,
                                            final int marginEveryCycles,
                                            final int processedCapacity) {
        return new ExecutionRuntime(
                new ExecutionRuntimeConfig("crypto", Duration.ofSeconds(timeoutSeconds), marginEveryCycles, 100L, processedCapacity, "crypto-execution-node-test"),
                journal,
                time,
                brokerSession,
                policy
        );
    }

    private static EventEnvelope orderIntentEvent(final ControllableTimeProvider time,
                                                  final UUID intentId,
                                                  final String payloadText,
                                                  final String canonicalId) {
        final byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.OrderIntentEvent,
                null,
                intentId.toString(),
                CanonicalId.parse(canonicalId),
                IdempotencyKeys.generate(null, EventType.OrderIntentEvent, payload),
                time.monotonicTime(),
                time.eventTime(),
                (byte) 1,
                payload
        );
    }
}
