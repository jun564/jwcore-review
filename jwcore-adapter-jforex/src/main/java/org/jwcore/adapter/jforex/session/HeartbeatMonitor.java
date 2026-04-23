package org.jwcore.adapter.jforex.session;

import java.time.Clock;

public final class HeartbeatMonitor {
    private final BrokerSession session;
    private final long timeoutMillis;
    private final Clock clock;
    private long lastHeartbeatMillis;

    public HeartbeatMonitor(final BrokerSession session,
                            final long timeoutMillis,
                            final Clock clock) {
        this.session = java.util.Objects.requireNonNull(session, "session cannot be null");
        this.timeoutMillis = timeoutMillis;
        this.clock = java.util.Objects.requireNonNull(clock, "clock cannot be null");
        this.lastHeartbeatMillis = clock.millis();
    }

    public synchronized void onHeartbeatSignal() {
        lastHeartbeatMillis = clock.millis();
    }

    public synchronized void checkHeartbeat() {
        if (clock.millis() - lastHeartbeatMillis > timeoutMillis) {
            session.onHeartbeatTimeout();
        }
    }
}
