package org.jwcore.execution.forex.runtime;

import org.jwcore.core.time.ControllableTimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.jwcore.execution.common.emit.EventEmitter;
import org.jwcore.execution.common.state.ExecutionState;
import org.jwcore.execution.forex.broker.StubBrokerSession;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionRuntimeTest {
    @Test
    void shouldEmitOrderSubmittedAfterBrokerSubmit() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 10, 100_000);

        final UUID intentId = UUID.randomUUID();
        journal.append(orderIntentEvent(time, intentId, "forex-account|BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));

        runtime.tickCycle();

        final var submittedEvents = journal.all().stream()
                .filter(e -> e.eventType() == EventType.OrderSubmittedEvent)
                .toList();
        assertEquals(1, submittedEvents.size());
        final EventEnvelope submitted = submittedEvents.get(0);
        assertEquals(intentId, submitted.correlationId());
        assertEquals("forex-execution-node-test", submitted.sourceProcessId());
        final String[] payload = new String(submitted.payload(), StandardCharsets.UTF_8).split("\\|", 6);
        assertEquals("forex-account", payload[0]);
        assertEquals(intentId.toString(), payload[1]);
        assertEquals(submitted.brokerOrderId(), payload[2]);
        assertEquals("0.1", payload[4]);
    }


    @Test
    void shouldEmitBrokerTimeoutAsOrderUnknownAfterSubmit() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 6, 10, 100_000);

        final UUID intentId = UUID.randomUUID();
        journal.append(orderIntentEvent(time, intentId, "forex-account|BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));
        runtime.tickCycle();

        time.advanceBy(Duration.ofSeconds(7));
        runtime.tickCycle();

        final var unknownEvents = journal.all().stream()
                .filter(e -> e.eventType() == EventType.OrderUnknownEvent)
                .toList();
        assertEquals(1, unknownEvents.size());
        assertEquals(intentId, unknownEvents.get(0).correlationId());
        assertEquals("BROKER_TIMEOUT", new String(unknownEvents.get(0).payload(), StandardCharsets.UTF_8).split("\\|", 3)[1]);
    }

    @Test
    void shouldProcessOrderIntentInRunStateAndEmitTimeoutAndMargin() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 2, 100_000);

        journal.append(orderIntentEvent(time, UUID.randomUUID(), "forex-account|BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));

        runtime.tickCycle();
        assertEquals(1, brokerSession.submitted().size());
        assertEquals(1, journal.all().stream().filter(e -> e.eventType() == EventType.OrderSubmittedEvent).count());
        assertEquals(1, brokerSession.brokerOrderIds().size());

        time.advanceBy(Duration.ofSeconds(5));
        runtime.tickCycle();
        assertTrue(journal.all().stream().noneMatch(e -> e.eventType() == EventType.OrderTimeoutEvent));
        assertTrue(journal.all().stream().anyMatch(e -> e.eventType() == EventType.MarginUpdateEvent));
    }

    @Test
    void shouldTransitionToSafeOnRiskDecisionForOwnAccount() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 5, 100_000);
        final EventEmitter emitter = new EventEmitter(journal, time);
        journal.append(emitter.createRiskDecisionEvent("forex", ExecutionState.SAFE, "test-safe").envelope());

        runtime.tickCycle();
        assertEquals(ExecutionState.SAFE, runtime.currentState());
    }

    @Test
    void shouldIgnoreRiskDecisionForOtherAccount() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 5, 100_000);
        final EventEmitter emitter = new EventEmitter(journal, time);
        journal.append(emitter.createRiskDecisionEvent("crypto", ExecutionState.HALT, "test-halt").envelope());

        runtime.tickCycle();
        assertEquals(ExecutionState.RUN, runtime.currentState());
    }

    @Test
    void shouldIgnoreOrderIntentInSafeStateButStillTriggerTimeouts() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.SAFE, 5, 30, 10, 100_000);

        final UUID rejectedIntentId = UUID.randomUUID();
        journal.append(orderIntentEvent(time, rejectedIntentId, "forex-account|BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));
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
        assertEquals("forex-execution-node-test", rejected.sourceProcessId());

        final UUID pendingIntent = UUID.randomUUID();
        journal.append(orderIntentEvent(time, pendingIntent, "forex-account|ETHUSD|0.20", "S07:I04:VA07-04:BA01"));
        final var runRuntime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 10, 100_000);
        runRuntime.tickCycle();
        assertEquals(1, runRuntime.pendingIntents());

        time.advanceBy(Duration.ofSeconds(5));
        runRuntime.tickCycle();
        assertTrue(journal.all().stream().noneMatch(e -> e.eventType() == EventType.OrderTimeoutEvent));
    }

    @Test
    void shouldRejectOrderIntentInHaltStateWithEvent() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.HALT, 5, 30, 10, 100_000);

        final UUID rejectedIntentId = UUID.randomUUID();
        journal.append(orderIntentEvent(time, rejectedIntentId, "forex-account|BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));
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
        assertEquals("forex-execution-node-test", rejected.sourceProcessId());

        final UUID pendingIntent = UUID.randomUUID();
        journal.append(orderIntentEvent(time, pendingIntent, "forex-account|ETHUSD|0.20", "S07:I04:VA07-04:BA01"));
        final var runRuntime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 10, 100_000);
        runRuntime.tickCycle();
        assertEquals(1, runRuntime.pendingIntents());

        time.advanceBy(Duration.ofSeconds(5));
        runRuntime.tickCycle();
        assertTrue(journal.all().stream().noneMatch(e -> e.eventType() == EventType.OrderTimeoutEvent));
    }

    @Test
    void shouldStayInKillStateAndIgnoreOrderIntents() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final EventEmitter emitter = new EventEmitter(journal, time);
        journal.append(emitter.createRiskDecisionEvent("forex", ExecutionState.KILL, "operator kill").envelope());
        journal.append(orderIntentEvent(time, UUID.randomUUID(), "forex-account|BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));

        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 10, 100_000);
        runtime.tickCycle();

        assertEquals(ExecutionState.KILL, runtime.currentState());
        assertEquals(0, brokerSession.submitted().size());
    }

    @Test
    void shouldFollowRunHaltSafeRunSequenceAcrossRiskDecisions() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 10, 100_000);
        final EventEmitter emitter = new EventEmitter(journal, time);

        journal.append(emitter.createRiskDecisionEvent("forex", ExecutionState.HALT, "halt").envelope());
        runtime.tickCycle();
        assertEquals(ExecutionState.HALT, runtime.currentState());

        journal.append(emitter.createRiskDecisionEvent("forex", ExecutionState.SAFE, "step down").envelope());
        time.advanceBy(Duration.ofMillis(1));
        runtime.tickCycle();
        assertEquals(ExecutionState.SAFE, runtime.currentState());

        journal.append(emitter.createRiskDecisionEvent("forex", ExecutionState.RUN, "resume").envelope());
        time.advanceBy(Duration.ofMillis(1));
        runtime.tickCycle();
        assertEquals(ExecutionState.RUN, runtime.currentState());
    }

    @Test
    void shouldHandleEmptyCycleAndEmitMarginOnlyOnConfiguredInterval() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 3, 100_000);

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
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 10, 100_000);

        journal.append(orderIntentEvent(time, UUID.randomUUID(), "forex-account|BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));
        journal.append(orderIntentEvent(time, UUID.randomUUID(), "forex-account|ETHUSD|0.20", "S07:I04:VA07-04:BA01"));
        journal.append(orderIntentEvent(time, UUID.randomUUID(), "forex-account|XAUUSD|0.30", "S07:I05:VA07-05:BA01"));
        runtime.tickCycle();

        time.advanceBy(Duration.ofSeconds(5));
        runtime.tickCycle();

        assertEquals(0, journal.all().stream().filter(e -> e.eventType() == EventType.OrderTimeoutEvent).count());
        assertEquals(3, runtime.pendingIntents());
    }

    @Test
    void shouldBoundProcessedEventIdsUsingConfiguredCapacity() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 100, 2);

        final UUID intentId = UUID.randomUUID();
        journal.append(orderIntentEvent(time, intentId, "forex-account|BTCUSDT|0.10", "S07:I03:VA07-03:BA01"));
        runtime.tickCycle();
        time.advanceBy(Duration.ofMillis(1));
        journal.append(orderIntentEvent(time, UUID.randomUUID(), "forex-account|ETHUSD|0.20", "S07:I04:VA07-04:BA01"));
        runtime.tickCycle();
        time.advanceBy(Duration.ofMillis(1));
        journal.append(orderIntentEvent(time, UUID.randomUUID(), "forex-account|XAUUSD|0.30", "S07:I05:VA07-05:BA01"));
        runtime.tickCycle();

        assertEquals(2, runtime.processedEvents());
    }

    @Test
    void shouldContinueTickCycleAfterBadEvent() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 100, 100_000);

        final EventEnvelope okFirst = orderIntentEvent(time, UUID.randomUUID(), "forex-account|BTCUSDT|0.10", "S07:I03:VA07-03:BA01");
        final EventEnvelope bad = orderIntentEvent(time, UUID.randomUUID(), "BROKEN_PAYLOAD", "S07:I04:VA07-04:BA01");
        final EventEnvelope okThird = orderIntentEvent(time, UUID.randomUUID(), "forex-account|ETHUSD|0.20", "S07:I05:VA07-05:BA01");
        journal.append(okFirst);
        journal.append(bad);
        journal.append(okThird);

        runtime.tickCycle();

        assertEquals(2, brokerSession.submitted().size());
        final var failedEvents = journal.all().stream()
                .filter(e -> e.eventType() == EventType.EventProcessingFailedEvent)
                .toList();
        assertEquals(1, failedEvents.size());
        assertEquals(bad.eventId(), extractFailedEventId(failedEvents.get(0)));

        time.advanceBy(Duration.ofSeconds(5));
        runtime.tickCycle();
        assertEquals(0, journal.all().stream().filter(e -> e.eventType() == EventType.OrderTimeoutEvent).count());
    }

    @Test
    void shouldEmitFailedEventForBrokenUuid() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var brokerSession = new StubBrokerSession();
        final var runtime = runtime(journal, time, brokerSession, snapshot -> ExecutionState.RUN, 5, 30, 100, 100_000);

        final EventEnvelope malformedUuidEvent = orderIntentEventWithLocalIntentId(
                time,
                "broken-uuid",
                "forex-account|BTCUSDT|0.10",
                "S07:I03:VA07-03:BA01"
        );
        journal.append(malformedUuidEvent);

        runtime.tickCycle();

        final var failedEvents = journal.all().stream()
                .filter(e -> e.eventType() == EventType.EventProcessingFailedEvent)
                .toList();
        assertEquals(1, failedEvents.size());
        assertEquals("java.lang.IllegalArgumentException", extractFailedErrorType(failedEvents.get(0)));
    }

    private static String extractRejectReasonCode(final EventEnvelope envelope) {
        return new String(envelope.payload(), StandardCharsets.UTF_8).split("\\|", 3)[1];
    }

    private static UUID extractFailedEventId(final EventEnvelope envelope) {
        final String[] parts = new String(envelope.payload(), StandardCharsets.UTF_8).split("\\|", 4);
        return UUID.fromString(parts[0]);
    }

    private static String extractFailedErrorType(final EventEnvelope envelope) {
        return new String(envelope.payload(), StandardCharsets.UTF_8).split("\\|", 4)[1];
    }

    private static ExecutionRuntime runtime(final InMemoryEventJournal journal,
                                            final ControllableTimeProvider time,
                                            final StubBrokerSession brokerSession,
                                            final org.jwcore.execution.forex.risk.LocalRiskPolicy policy,
                                            final long executionTimeoutSeconds,
                                            final long brokerTimeoutSeconds,
                                            final int marginEveryCycles,
                                            final int processedCapacity) {
        return new ExecutionRuntime(
                new ExecutionRuntimeConfig("forex", Duration.ofSeconds(executionTimeoutSeconds), Duration.ofSeconds(brokerTimeoutSeconds), marginEveryCycles, 100L, processedCapacity, "forex-execution-node-test"),
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
        return orderIntentEventWithLocalIntentId(time, intentId.toString(), payloadText, canonicalId);
    }

    private static EventEnvelope orderIntentEventWithLocalIntentId(final ControllableTimeProvider time,
                                                                   final String localIntentId,
                                                                   final String payloadText,
                                                                   final String canonicalId) {
        final byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
        UUID correlationId = null;
        try {
            correlationId = UUID.fromString(localIntentId);
        } catch (final IllegalArgumentException ignored) {
            // intentionally ignored for malformed UUID scenarios
        }
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.OrderIntentEvent,
                null,
                localIntentId,
                CanonicalId.parse(canonicalId),
                IdempotencyKeys.generate(null, EventType.OrderIntentEvent, payload),
                time.monotonicTime(),
                time.eventTime(),
                (byte) 1,
                payload,
                "forex-runtime-test",
                correlationId
        );
    }
}
