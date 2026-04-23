package org.jwcore.adapter.jforex.session;

public enum BrokerSessionState {
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    RECONCILING,
    FAILED
}
