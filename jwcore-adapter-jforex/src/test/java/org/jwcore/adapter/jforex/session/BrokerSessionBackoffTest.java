package org.jwcore.adapter.jforex.session;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrokerSessionBackoffTest {

    @Test
    void shouldFailAfterFiveAttemptsWithExponentialDelay() {
        final MockBroker broker = new MockBroker();
        broker.enqueueReconnectResult(false);
        broker.enqueueReconnectResult(false);
        broker.enqueueReconnectResult(false);
        broker.enqueueReconnectResult(false);
        broker.enqueueReconnectResult(false);

        final TestScheduler scheduler = new TestScheduler();
        final BrokerSession session = new BrokerSession(scheduler, broker::reconnect, fixedClock());
        session.onHeartbeatTimeout();
        session.startReconnect();

        scheduler.runAll();

        assertEquals(java.util.List.of(1L, 2L, 4L, 8L, 16L), scheduler.delaysSeconds());
        assertEquals(BrokerSessionState.FAILED, session.state());
        assertEquals(5, session.reconnectAttempts());
    }

    @Test
    void shouldResetCounterOnThirdAttemptSuccess() {
        final MockBroker broker = new MockBroker();
        broker.enqueueReconnectResult(false);
        broker.enqueueReconnectResult(false);
        broker.enqueueReconnectResult(true);

        final TestScheduler scheduler = new TestScheduler();
        final BrokerSession session = new BrokerSession(scheduler, broker::reconnect, fixedClock());
        session.onHeartbeatTimeout();
        session.startReconnect();

        scheduler.runAll();

        assertEquals(BrokerSessionState.RECONCILING, session.state());
        assertEquals(0, session.reconnectAttempts());
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-04-23T10:00:00Z"), ZoneOffset.UTC);
    }
}
