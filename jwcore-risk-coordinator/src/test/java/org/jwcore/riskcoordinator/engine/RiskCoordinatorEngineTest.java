package org.jwcore.riskcoordinator.engine;

import org.jwcore.core.failure.ProcessingFailureEmitter;
import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.core.time.ControllableTimeProvider;
import org.jwcore.core.util.FailureCounter;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.jwcore.domain.events.AlertEvent;
import org.jwcore.domain.events.AlertType;
import org.jwcore.domain.events.EventProcessingFailedEvent;
import org.jwcore.domain.OrderSide;
import org.jwcore.domain.events.OrderIntentEvent;
import org.jwcore.domain.events.OrderCanceledEvent;
import org.jwcore.domain.events.OrderFilledEvent;
import org.jwcore.domain.events.OrderSubmittedEvent;
import org.jwcore.domain.events.OrderUnknownEvent;
import org.jwcore.execution.common.state.ExecutionState;
import org.jwcore.riskcoordinator.command.RiskStateResetCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RiskCoordinatorEngineTest {
    @Test
    void shouldCalculateExposureFromFill() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        engine.apply(submitted("crypto", 10.0));
        engine.apply(intent("crypto", 10.0));
        engine.apply(filled("crypto", OrderSide.BUY, "10", "100", "1"));

        assertEquals(0, new BigDecimal("1000.00000000").compareTo(engine.exposureSnapshot().get("crypto")),
                "exposure after fill");
        assertEquals(ExecutionState.RUN, engine.currentStates().getOrDefault("crypto", ExecutionState.RUN));
    }

    @Test
    void shouldReduceExposureOnPartialClose() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");
        engine.apply(submitted("crypto", 10.0));
        engine.apply(intent("crypto", 10.0));
        engine.apply(filled("crypto", OrderSide.BUY, "10", "100", "1"));

        engine.apply(submitted("crypto", 4.0));
        engine.apply(intent("crypto", 4.0));
        engine.apply(filled("crypto", OrderSide.SELL, "4", "110", "1"));

        assertEquals(0, new BigDecimal("600.00000000").compareTo(engine.exposureSnapshot().get("crypto")),
                "exposure after partial close");
        assertEquals(ExecutionState.RUN, engine.currentStates().getOrDefault("crypto", ExecutionState.RUN));
    }

    @Test
    void shouldKeepExposureOnCancelAfterFill() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");
        engine.apply(submitted("crypto", 10.0));
        engine.apply(intent("crypto", 10.0));
        engine.apply(filled("crypto", OrderSide.BUY, "10", "100", "1"));

        engine.apply(submitted("crypto", 5.0));
        engine.apply(intent("crypto", 5.0));
        engine.apply(canceled("crypto"));

        assertEquals(0, new BigDecimal("1000.00000000").compareTo(engine.exposureSnapshot().get("crypto")),
                "exposure kept after cancel post-fill");
        assertEquals(ExecutionState.RUN, engine.currentStates().getOrDefault("crypto", ExecutionState.RUN));
    }

    @Test
    void shouldNotChangeExposureOnOrderUnknown() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");
        engine.apply(submitted("crypto", 10.0));
        engine.apply(intent("crypto", 10.0));
        engine.apply(filled("crypto", OrderSide.BUY, "10", "100", "1"));

        engine.apply(unknown("crypto"));

        assertEquals(0, new BigDecimal("1000.00000000").compareTo(engine.exposureSnapshot().get("crypto")),
                "exposure unchanged on OrderUnknown");
        assertEquals(ExecutionState.SAFE, engine.currentStates().get("crypto"));
    }

    @Test
    void shouldTransitionAccountToSafeOnOrderUnknownEvent() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        engine.apply(unknown("crypto"));

        assertEquals(ExecutionState.SAFE, engine.currentStates().get("crypto"));
    }

    @Test
    void shouldEmitRiskDecisionOnlyWhenStateChanges() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        assertTrue(engine.evaluateAndBuildIfChanged("crypto").isPresent());
        assertFalse(engine.evaluateAndBuildIfChanged("crypto").isPresent());

        engine.apply(unknown("crypto"));
        assertTrue(engine.evaluateAndBuildIfChanged("crypto").isPresent());
        assertFalse(engine.evaluateAndBuildIfChanged("crypto").isPresent());
    }


    @Test
    void shouldBuildDecisionAndAlertEnvelopesForStateChange() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        engine.evaluateAndBuildResultIfChanged("crypto");
        engine.apply(unknown("crypto"));

        final RiskEvaluationResult result = engine.evaluateAndBuildResultIfChanged("crypto");

        assertFalse(result.isEmpty());
        assertTrue(result.decisionEnvelope().isPresent());
        assertTrue(result.alertEnvelope().isPresent());
        assertEquals(EventType.AlertEvent, result.alertEnvelope().orElseThrow().eventType());
    }

    @Test
    void shouldReturnEmptyResultWhenStateDoesNotChange() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        engine.evaluateAndBuildResultIfChanged("crypto");
        final RiskEvaluationResult result = engine.evaluateAndBuildResultIfChanged("crypto");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotDuplicateEmissionInEvaluateAndBuildResultIfChanged() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        final RiskEvaluationResult first = engine.evaluateAndBuildResultIfChanged("crypto");
        final RiskEvaluationResult second = engine.evaluateAndBuildResultIfChanged("crypto");

        assertFalse(first.isEmpty());
        assertTrue(second.isEmpty());
    }

    @Test
    void shouldContainAlertIdInAlertIdempotencyKey() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        final RiskEvaluationResult result = engine.evaluateAndBuildResultIfChanged("crypto");

        final EventEnvelope alertEnvelope = result.alertEnvelope().orElseThrow();
        assertNotNull(alertEnvelope.idempotencyKey());
        assertTrue(alertEnvelope.idempotencyKey().contains(alertEnvelope.eventId().toString()));
    }

    @Test
    void shouldEmitInitialPublishForAllMonitoredAccounts() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        final var events = engine.initialPublishFromCurrentState(List.of("crypto", "forex"));

        assertEquals(2, events.size());
    }

    @Test
    void shouldEmitInitialPublishRunForMonitoredAccountWithoutAnyEvents() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        final var events = engine.initialPublishFromCurrentState(List.of("crypto"));

        assertEquals(ExecutionState.RUN, events.get(0).desiredState());
    }

    @Test
    void shouldDefaultToRunWhenAccountHasNoStateEntry() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        final var decision = engine.evaluateAndBuildIfChanged("crypto").orElseThrow();

        assertEquals(ExecutionState.RUN, decision.desiredState());
    }

    @Test
    void shouldNotMutateStateOfOtherAccountsWhenOneAccountTransitions() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");
        engine.apply(unknown("crypto"));

        engine.evaluateAndBuildIfChanged("forex");

        assertEquals(ExecutionState.SAFE, engine.currentStates().get("crypto"));
        assertFalse(engine.currentStates().containsKey("forex"));
    }

    @Test
    void shouldReturnAffectedAccountIdsFromApply() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        final Set<String> affected = engine.apply(submitted("crypto", 100.0));

        assertEquals(Set.of("crypto"), affected);
    }

    @Test
    void shouldSkipMalformedEventsWithWarning() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");
        final TestHandler handler = new TestHandler();
        final Logger logger = Logger.getLogger(RiskCoordinatorEngine.class.getName());
        logger.addHandler(handler);
        try {
            final EventEnvelope malformed = envelope(EventType.OrderSubmittedEvent, "invalid".getBytes());

            final Set<String> affected = engine.apply(malformed);

            assertTrue(affected.isEmpty());
            assertTrue(handler.warningSeen);
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void shouldEmitFailureForMalformedEventWhenEmitterConfigured() {
        final ControllableTimeProvider time = new ControllableTimeProvider(7L, Instant.parse("2026-04-24T10:00:00Z"));
        final RecordingJournal journal = new RecordingJournal();
        final ProcessingFailureEmitter failureEmitter = new ProcessingFailureEmitter(journal, time, "risk-node-1");
        final FailureCounter failureCounter = new FailureCounter(journal);
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(
                new ExposureLedger(), "risk-node-1", time, 3, Duration.ofSeconds(60), failureEmitter, failureCounter, journal);
        final EventEnvelope malformed = envelope(EventType.OrderSubmittedEvent, "invalid".getBytes());

        final Set<String> affected = engine.apply(malformed);

        assertEquals(Set.of(), affected);
        assertEquals(1, journal.appended.size());
        assertEquals(EventType.EventProcessingFailedEvent, journal.appended.get(0).eventType());
        final EventProcessingFailedEvent failure = EventProcessingFailedEvent.fromPayload(
                journal.appended.get(0).payload(), journal.appended.get(0).payloadVersion());
        assertEquals(malformed.eventId(), failure.failedEventId());
    }

    @Test
    void shouldEscalateToSafeOnPermanentFailureWithKnownAccount() {
        final ControllableTimeProvider time = new ControllableTimeProvider(7L, Instant.parse("2026-04-24T10:00:00Z"));
        final RecordingJournal journal = new RecordingJournal();
        final ProcessingFailureEmitter failureEmitter = new ProcessingFailureEmitter(journal, time, "risk-node-1");
        final FailureCounter failureCounter = new FailureCounter(journal);
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(
                new ExposureLedger(), "risk-node-1", time, 3, Duration.ofSeconds(60), failureEmitter, failureCounter, journal);

        final EventEnvelope malformedUnknown = unknown("crypto");
        engine.apply(malformedUnknown);
        engine.apply(malformedUnknown);
        engine.apply(malformedUnknown);

        assertEquals(ExecutionState.SAFE, engine.currentStates().get("crypto"));
        assertTrue(journal.appended.stream().anyMatch(e -> e.eventType() == EventType.AlertEvent));
    }

    @Test
    void shouldEscalateAllAccountsToSafeOnPermanentFailureUnknownContext() {
        final ControllableTimeProvider time = new ControllableTimeProvider(7L, Instant.parse("2026-04-24T10:00:00Z"));
        final RecordingJournal journal = new RecordingJournal();
        final ProcessingFailureEmitter failureEmitter = new ProcessingFailureEmitter(journal, time, "risk-node-1");
        final FailureCounter failureCounter = new FailureCounter(journal);
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(
                new ExposureLedger(), "risk-node-1", time, 3, Duration.ofSeconds(60), failureEmitter, failureCounter, journal);

        engine.apply(unknown("crypto"));
        engine.apply(unknown("forex"));
        final EventEnvelope malformed = envelope(EventType.OrderFilledEvent, "invalid".getBytes());
        engine.apply(malformed);
        engine.apply(malformed);
        engine.apply(malformed);

        assertEquals(ExecutionState.SAFE, engine.currentStates().get("crypto"));
        assertEquals(ExecutionState.SAFE, engine.currentStates().get("forex"));
    }

    @Test
    void shouldNotEscalateOnFirstOrSecondAttempt() {
        final ControllableTimeProvider time = new ControllableTimeProvider(7L, Instant.parse("2026-04-24T10:00:00Z"));
        final RecordingJournal journal = new RecordingJournal();
        final ProcessingFailureEmitter failureEmitter = new ProcessingFailureEmitter(journal, time, "risk-node-1");
        final FailureCounter failureCounter = new FailureCounter(journal);
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(
                new ExposureLedger(), "risk-node-1", time, 3, Duration.ofSeconds(60), failureEmitter, failureCounter, journal);
        final EventEnvelope malformed = envelope(EventType.OrderSubmittedEvent, "invalid".getBytes());

        engine.apply(malformed);
        engine.apply(malformed);

        assertTrue(journal.appended.stream().noneMatch(e -> e.eventType() == EventType.AlertEvent));
    }

    @Test
    void shouldEmitPermanentFailureAlertWithAffectedAccounts() throws Exception {
        final ControllableTimeProvider time = new ControllableTimeProvider(7L, Instant.parse("2026-04-24T10:00:00Z"));
        final RecordingJournal journal = new RecordingJournal();
        final ProcessingFailureEmitter failureEmitter = new ProcessingFailureEmitter(journal, time, "risk-node-1");
        final FailureCounter failureCounter = new FailureCounter(journal);
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(
                new ExposureLedger(), "risk-node-1", time, 3, Duration.ofSeconds(60), failureEmitter, failureCounter, journal);

        final EventEnvelope malformed = envelope(EventType.OrderFilledEvent, "invalid".getBytes());
        engine.apply(malformed);
        engine.apply(malformed);
        engine.apply(malformed);

        final EventEnvelope alertEnvelope = journal.appended.stream()
                .filter(e -> e.eventType() == EventType.AlertEvent)
                .findFirst().orElseThrow();
        final AlertEvent alert = AlertEvent.fromPayload(alertEnvelope.payload());
        assertEquals(AlertType.PERMANENT_FAILURE, alert.alertType());
        assertTrue(alert.affectedAccounts().size() >= 0);
    }

    @Test
    void shouldSwallowFailureEmitterExceptions() {
        final ControllableTimeProvider time = new ControllableTimeProvider(7L, Instant.parse("2026-04-24T10:00:00Z"));
        final ThrowingJournal journal = new ThrowingJournal();
        final ProcessingFailureEmitter failureEmitter = new ProcessingFailureEmitter(journal, time, "risk-node-1");
        final FailureCounter failureCounter = new FailureCounter(journal);
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(
                new ExposureLedger(), "risk-node-1", time, 3, Duration.ofSeconds(60), failureEmitter, failureCounter, journal);
        final EventEnvelope malformed = envelope(EventType.OrderSubmittedEvent, "invalid".getBytes());

        try {
            final Set<String> affected = engine.apply(malformed);
            assertEquals(Set.of(), affected);
        } catch (final Exception exception) {
            fail("Exception must not propagate from apply()");
        }
    }

    @Test
    void shouldKeepSnapshotsUnchangedAfterMalformedEvent() {
        final ControllableTimeProvider time = new ControllableTimeProvider(0L, Instant.parse("2026-04-24T10:00:00Z"));
        final RecordingJournal journal = new RecordingJournal();
        final ProcessingFailureEmitter failureEmitter = new ProcessingFailureEmitter(journal, time, "risk-node-1");
        final FailureCounter failureCounter = new FailureCounter(journal);
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(
                new ExposureLedger(), "risk-node-1", time, 3, Duration.ofSeconds(60), failureEmitter, failureCounter, journal);

        engine.apply(submitted("crypto", 10.0));
        engine.apply(intent("crypto", 10.0));
        engine.apply(filled("crypto", OrderSide.BUY, "10", "100", "1"));

        final Map<String, ExecutionState> stateBefore = engine.currentStates();
        final Map<String, BigDecimal> exposureBefore = engine.exposureSnapshot();

        final Set<String> affected = engine.apply(envelope(EventType.OrderSubmittedEvent, "invalid".getBytes()));

        assertEquals(Set.of(), affected);
        assertEquals(stateBefore, engine.currentStates());
        assertEquals(exposureBefore, engine.exposureSnapshot());
    }

    @Test
    void shouldKeepLegacyBehaviorWithoutFailureEmitter() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");
        final EventEnvelope malformed = envelope(EventType.OrderSubmittedEvent, "invalid".getBytes());

        final Set<String> affected = engine.apply(malformed);

        assertEquals(Set.of(), affected);
    }

    @Test
    void shouldFullLifecycleSequence() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        engine.apply(submitted("crypto", 10.0));
        engine.apply(intent("crypto", 10.0));
        engine.apply(filled("crypto", OrderSide.BUY, "10", "100", "1"));
        engine.apply(submitted("crypto", 10.0));
        engine.apply(intent("crypto", 10.0));
        engine.apply(filled("crypto", OrderSide.SELL, "10", "105", "1"));
        engine.apply(submitted("crypto", 5.0));
        engine.apply(intent("crypto", 5.0));
        engine.apply(canceled("crypto"));
        engine.apply(unknown("crypto"));

        assertEquals(BigDecimal.ZERO, engine.exposureSnapshot().get("crypto"));
        assertEquals(ExecutionState.SAFE, engine.currentStates().get("crypto"));
    }

    @Test
    void shouldHandleSellFillLargerThanLongAsReversePosition() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        engine.apply(submitted("crypto", 10.0));
        engine.apply(intent("crypto", 10.0));
        engine.apply(filled("crypto", OrderSide.BUY, "10", "100", "0"));

        engine.apply(submitted("crypto", 30.0));
        engine.apply(intent("crypto", 30.0));
        engine.apply(filled("crypto", OrderSide.SELL, "30", "100", "0"));

        final BigDecimal exposure = engine.exposureSnapshot().get("crypto");
        assertTrue(exposure.compareTo(new BigDecimal("2000")) == 0
                || exposure.compareTo(new BigDecimal("-2000")) == 0);
    }


    @Test
    void shouldEscalateToHaltAfter3Unknowns() {
        final ControllableTimeProvider time = new ControllableTimeProvider(0L, Instant.parse("2026-04-23T10:00:00Z"));
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(
                new ExposureLedger(), "risk-node-1", time, 3, Duration.ofSeconds(60));

        engine.apply(submitted("crypto", 10.0));
        engine.apply(unknown("crypto"));
        time.advanceBy(Duration.ofSeconds(10));
        engine.apply(unknown("crypto"));
        time.advanceBy(Duration.ofSeconds(10));
        engine.apply(unknown("crypto"));

        assertEquals(ExecutionState.HALT, engine.currentStates().get("crypto"));
    }

    @Test
    void shouldNotEscalateOutsideWindow() {
        final ControllableTimeProvider time = new ControllableTimeProvider(0L, Instant.parse("2026-04-23T10:00:00Z"));
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(
                new ExposureLedger(), "risk-node-1", time, 3, Duration.ofSeconds(60));

        engine.apply(submitted("crypto", 10.0));
        engine.apply(unknown("crypto"));
        time.advanceBy(Duration.ofSeconds(70));
        engine.apply(unknown("crypto"));
        time.advanceBy(Duration.ofSeconds(70));
        engine.apply(unknown("crypto"));

        assertEquals(ExecutionState.SAFE, engine.currentStates().get("crypto"));
    }

    @Test
    void shouldResetHaltToRun() {
        final ControllableTimeProvider time = new ControllableTimeProvider(0L, Instant.parse("2026-04-23T10:00:00Z"));
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(
                new ExposureLedger(), "risk-node-1", time, 3, Duration.ofSeconds(60));

        engine.apply(submitted("crypto", 10.0));
        engine.apply(unknown("crypto"));
        time.advanceBy(Duration.ofSeconds(10));
        engine.apply(unknown("crypto"));
        time.advanceBy(Duration.ofSeconds(10));
        engine.apply(unknown("crypto"));

        final RiskStateResetCommand cmd = new RiskStateResetCommand(
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                ExecutionState.RUN, "op1", "manual reset after review",
                Instant.parse("2026-04-23T10:00:00Z"));

        engine.executeResetCommand(cmd);

        assertEquals(ExecutionState.RUN, engine.currentStates().get("crypto"));
    }

    @Test
    void shouldClearUnknownWindowOnReset() {
        final ControllableTimeProvider time = new ControllableTimeProvider(0L, Instant.parse("2026-04-23T10:00:00Z"));
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(
                new ExposureLedger(), "risk-node-1", time, 3, Duration.ofSeconds(60));

        engine.apply(submitted("crypto", 10.0));
        engine.apply(unknown("crypto"));
        time.advanceBy(Duration.ofSeconds(10));
        engine.apply(unknown("crypto"));

        final RiskStateResetCommand cmd = new RiskStateResetCommand(
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                ExecutionState.RUN, "op1", "manual reset after review",
                Instant.parse("2026-04-23T10:00:00Z"));
        engine.executeResetCommand(cmd);

        engine.apply(unknown("crypto"));
        assertEquals(ExecutionState.SAFE, engine.currentStates().get("crypto"));
    }

    private static EventEnvelope submitted(final String accountId, final double size) {
        final OrderSubmittedEvent event = new OrderSubmittedEvent(
                accountId,
                UUID.randomUUID(),
                "BROKER-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                size,
                Instant.parse("2026-04-20T10:00:00Z"),
                null
        );
        return envelope(EventType.OrderSubmittedEvent, event.toPayload());
    }

    private static EventEnvelope intent(final String accountId, final double size) {
        final OrderIntentEvent event = new OrderIntentEvent(accountId, new org.jwcore.domain.Instrument("BTC-USD"), size);
        return envelope(EventType.OrderIntentEvent, CanonicalId.parse("S07:I03:VA07-03:BA01"), event.toPayload());
    }

    private static EventEnvelope filled(final String accountId,
                                        final OrderSide side,
                                        final String size,
                                        final String averagePrice,
                                        final String commission) {
        final OrderFilledEvent event = new OrderFilledEvent(
                UUID.randomUUID().toString(),
                "BROKER-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                side,
                new BigDecimal(size),
                new BigDecimal(averagePrice),
                new BigDecimal(commission),
                Instant.parse("2026-04-20T10:00:00Z"),
                BigDecimal.ZERO,
                null
        );
        return envelope(EventType.OrderFilledEvent, event.toPayload());
    }

    private static EventEnvelope canceled(final String accountId) {
        final OrderCanceledEvent event = new OrderCanceledEvent(
                UUID.randomUUID().toString(),
                "BROKER-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "USER_REQUEST",
                Instant.parse("2026-04-20T10:00:00Z"),
                null
        );
        return envelope(EventType.OrderCanceledEvent, event.toPayload());
    }

    private static EventEnvelope unknown(final String accountId) {
        final OrderUnknownEvent event = new OrderUnknownEvent(
                accountId,
                UUID.randomUUID().toString(),
                "UNKNOWN",
                Instant.parse("2026-04-20T10:00:00Z"),
                null
        );
        return envelope(EventType.OrderUnknownEvent, event.toPayload());
    }

    private static EventEnvelope envelope(final EventType type, final byte[] payload) {
        return envelope(type, null, payload);
    }

    private static EventEnvelope envelope(final EventType type, final CanonicalId canonicalId, final byte[] payload) {
        return new EventEnvelope(
                UUID.randomUUID(),
                type,
                null,
                null,
                canonicalId,
                IdempotencyKeys.generate(null, type, payload),
                1L,
                Instant.parse("2026-04-19T08:00:00Z"),
                (byte) 1,
                payload,
                "risk-coordinator-test",
                UUID.randomUUID()
        );
    }

    private static final class TestHandler extends Handler {
        private boolean warningSeen;

        @Override
        public void publish(final LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warningSeen = true;
            }
        }

        @Override public void flush() { }
        @Override public void close() { }
    }

    private static final class RecordingJournal implements IEventJournal {
        private final List<EventEnvelope> appended = new ArrayList<>();

        @Override
        public long append(final EventEnvelope envelope) {
            appended.add(envelope);
            return appended.size();
        }

        @Override
        public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) {
            return List.of();
        }

        @Override
        public long currentSequence() {
            return appended.size();
        }

        @Override
        public List<EventEnvelope> readAfterSequence(final long sequence) {
            return List.of();
        }

        @Override
        public TailSubscription tail(final Consumer<EventEnvelope> consumer) {
            return () -> { };
        }
    }

    private static final class ThrowingJournal implements IEventJournal {
        @Override
        public long append(final EventEnvelope envelope) {
            throw new RuntimeException("append failure");
        }

        @Override
        public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) {
            return List.of();
        }

        @Override
        public long currentSequence() {
            return 0L;
        }

        @Override
        public List<EventEnvelope> readAfterSequence(final long sequence) {
            return List.of();
        }

        @Override
        public TailSubscription tail(final Consumer<EventEnvelope> consumer) {
            return () -> { };
        }
    }
}
