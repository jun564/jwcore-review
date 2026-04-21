package org.jwcore.riskcoordinator.engine;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
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
    void shouldAddExposureOnOrderSubmitted() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");

        engine.apply(submitted("crypto", 100.0));

        assertEquals(new BigDecimal("100.0"), engine.exposureSnapshot().get("crypto"));
    }

    @Test
    void shouldSubtractExposureOnOrderFilled() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");
        engine.apply(submitted("crypto", 100.0));

        engine.apply(filled("crypto", "40"));

        assertEquals(new BigDecimal("60.0"), engine.exposureSnapshot().get("crypto"));
    }

    @Test
    void shouldSubtractExposureOnOrderCanceled() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");
        engine.apply(submitted("crypto", 100.0));

        engine.apply(canceled("crypto", "30"));

        assertEquals(new BigDecimal("70.0"), engine.exposureSnapshot().get("crypto"));
    }

    @Test
    void shouldNotChangeExposureOnOrderUnknown() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");
        engine.apply(submitted("crypto", 100.0));

        engine.apply(unknown("crypto"));

        assertEquals(new BigDecimal("100.0"), engine.exposureSnapshot().get("crypto"));
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

        engine.apply(submitted("crypto", 100.0));
        engine.apply(submitted("crypto", 50.0));
        engine.apply(filled("crypto", "100"));
        engine.apply(unknown("crypto"));

        assertEquals(new BigDecimal("50.0"), engine.exposureSnapshot().get("crypto"));
        assertEquals(ExecutionState.SAFE, engine.currentStates().get("crypto"));
    }

    @Test
    void shouldLogWarningWhenFilledWouldDriveExposureBelowZero() {
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine("risk-node-1");
        engine.apply(submitted("crypto", 10.0));

        final TestHandler handler = new TestHandler();
        final Logger logger = Logger.getLogger(RiskCoordinatorEngine.class.getName());
        logger.addHandler(handler);
        try {
            engine.apply(filled("crypto", "30"));
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

    private static EventEnvelope filled(final String accountId, final String size) {
        final OrderFilledEvent event = new OrderFilledEvent(
                accountId,
                UUID.randomUUID().toString(),
                "BROKER-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                new BigDecimal(size),
                Instant.parse("2026-04-20T10:00:00Z"),
                null
        );
        return envelope(EventType.OrderFilledEvent, event.toPayload());
    }

    private static EventEnvelope canceled(final String accountId, final String size) {
        final OrderCanceledEvent event = new OrderCanceledEvent(
                accountId,
                UUID.randomUUID().toString(),
                "BROKER-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                new BigDecimal(size),
                "reason",
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
        return new EventEnvelope(
                UUID.randomUUID(),
                type,
                null,
                null,
                null,
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
