package org.jwcore.adapter.jforex.session;

import org.jwcore.domain.events.DriftClassification;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerSessionStateMachineTest {

    @Test
    void shouldAllowConfiguredTransitions() {
        final BrokerSession session = new BrokerSession(new TestScheduler(), () -> false, fixedClock());
        session.onHeartbeatTimeout();
        assertEquals(BrokerSessionState.DISCONNECTED, session.state());

        session.startReconnect();
        assertEquals(BrokerSessionState.RECONNECTING, session.state());

        session.onReconnectSuccess();
        assertEquals(BrokerSessionState.RECONCILING, session.state());

        session.onReconcileComplete(DriftClassification.NONE);
        assertEquals(BrokerSessionState.CONNECTED, session.state());
    }

    @Test
    void shouldRejectIllegalTransitions() {
        final BrokerSession session = new BrokerSession(new TestScheduler(), () -> false, fixedClock());
        assertThrows(IllegalStateException.class, () -> session.transitionTo(BrokerSessionState.RECONCILING));

        session.onHeartbeatTimeout();
        assertThrows(IllegalStateException.class, () -> session.transitionTo(BrokerSessionState.CONNECTED));
    }

    @Test
    void shouldResetAttemptsWhenEnteringReconciling() {
        final TestScheduler scheduler = new TestScheduler();
        final BrokerSession session = new BrokerSession(scheduler, () -> false, fixedClock());

        session.onHeartbeatTimeout();
        session.startReconnect();
        scheduler.runNext();
        assertTrue(session.reconnectAttempts() > 0);

        session.onReconnectSuccess();
        assertEquals(0, session.reconnectAttempts());
    }

    @Test
    void failedStateShouldBeTerminal() {
        final BrokerSession session = new BrokerSession(new TestScheduler(), () -> false, fixedClock());
        session.onHeartbeatTimeout();
        session.startReconnect();
        session.transitionTo(BrokerSessionState.FAILED);

        assertThrows(IllegalStateException.class, () -> session.transitionTo(BrokerSessionState.CONNECTED));
        assertThrows(IllegalStateException.class, () -> session.transitionTo(BrokerSessionState.RECONNECTING));
        assertTrue(session.failedAt().isPresent());
    }

    @Test
    void shouldBlockIntentOutsideConnectedAndOnActiveDrift() {
        final BrokerSession session = new BrokerSession(new TestScheduler(), () -> false, fixedClock());
        final var cid = org.jwcore.domain.CanonicalId.parse("S07:I03:VA07-03:BA01");
        assertTrue(session.canAcceptIntent(cid));

        session.updateActiveDrift(cid, DriftClassification.FATAL);
        assertFalse(session.canAcceptIntent(cid));
        session.updateActiveDrift(cid, DriftClassification.MINOR);
        assertTrue(session.canAcceptIntent(cid));

        session.onHeartbeatTimeout();
        assertFalse(session.canAcceptIntent(cid));
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-04-23T10:00:00Z"), ZoneOffset.UTC);
    }
}
