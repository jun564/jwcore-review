package org.jwcore.riskcoordinator.engine;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.jwcore.domain.OrderSide;
import org.jwcore.domain.events.OrderIntentEvent;
import org.jwcore.domain.events.OrderCanceledEvent;
import org.jwcore.domain.events.OrderFilledEvent;
import org.jwcore.domain.events.OrderSubmittedEvent;
import org.jwcore.domain.events.OrderUnknownEvent;
import org.jwcore.execution.common.state.ExecutionState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void shouldLogWarningWhenFilledWouldDriveExposureBelowZero() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");
        engine.apply(submitted("crypto", 10.0));
        engine.apply(intent("crypto", 10.0));

        final TestHandler handler = new TestHandler();
        final Logger logger = Logger.getLogger(RiskCoordinatorEngine.class.getName());
        logger.addHandler(handler);
        try {
            engine.apply(filled("crypto", OrderSide.BUY, "30", "1", "0"));
        } finally {
            logger.removeHandler(handler);
        }

        assertEquals(BigDecimal.ZERO, engine.exposureSnapshot().get("crypto"));
        assertTrue(handler.warningSeen);
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
}
