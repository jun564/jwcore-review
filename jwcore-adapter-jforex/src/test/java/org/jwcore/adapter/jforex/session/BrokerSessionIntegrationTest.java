package org.jwcore.adapter.jforex.session;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.events.DriftClassification;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BrokerSessionIntegrationTest {

    @Test
    void shouldReconnectReconcileAndReturnConnected() {
        final MockBroker broker = new MockBroker();
        broker.enqueueReconnectResult(false);
        broker.enqueueReconnectResult(true);

        final TestScheduler scheduler = new TestScheduler();
        final BrokerSession session = new BrokerSession(scheduler, broker::reconnect, fixedClock());

        session.onHeartbeatTimeout();
        session.startReconnect();
        scheduler.runAll();
        assertEquals(BrokerSessionState.RECONCILING, session.state());

        session.onReconcileComplete(DriftClassification.MINOR);
        assertEquals(BrokerSessionState.CONNECTED, session.state());
    }

    @Test
    void shouldGoFailedAfterFiveReconnectFailures() {
        final MockBroker broker = new MockBroker();
        for (int i = 0; i < 5; i++) {
            broker.enqueueReconnectResult(false);
        }

        final TestScheduler scheduler = new TestScheduler();
        final BrokerSession session = new BrokerSession(scheduler, broker::reconnect, fixedClock());
        session.onBrokerException(new RuntimeException("x"));
        session.startReconnect();
        scheduler.runAll();

        assertEquals(BrokerSessionState.FAILED, session.state());
    }

    @Test
    void shouldBlockIntentInFailedState() {
        final MockBroker broker = new MockBroker();
        for (int i = 0; i < 5; i++) {
            broker.enqueueReconnectResult(false);
        }
        final TestScheduler scheduler = new TestScheduler();
        final BrokerSession session = new BrokerSession(scheduler, broker::reconnect, fixedClock());
        final CanonicalId cid = CanonicalId.parse("S07:I03:VA07-03:BA01");

        session.onHeartbeatTimeout();
        session.startReconnect();
        scheduler.runAll();

        assertFalse(session.canAcceptIntent(cid));
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-04-23T10:00:00Z"), ZoneOffset.UTC);
    }
}
