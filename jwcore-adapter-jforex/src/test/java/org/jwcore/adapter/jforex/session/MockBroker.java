package org.jwcore.adapter.jforex.session;

import org.jwcore.domain.CanonicalId;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

final class MockBroker {
    private final Queue<Boolean> reconnectResults = new ArrayDeque<>();
    private Map<CanonicalId, ReconcileSnapshot> snapshot = Map.of();

    void enqueueReconnectResult(final boolean result) {
        reconnectResults.add(result);
    }

    boolean reconnect() {
        if (reconnectResults.isEmpty()) {
            return false;
        }
        return reconnectResults.remove();
    }

    void setSnapshot(final Map<CanonicalId, ReconcileSnapshot> snapshot) {
        this.snapshot = snapshot;
    }

    Map<CanonicalId, ReconcileSnapshot> snapshot() {
        return snapshot;
    }
}
