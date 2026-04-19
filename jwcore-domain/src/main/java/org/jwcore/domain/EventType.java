package org.jwcore.domain;

public enum EventType {
    MarketDataEvent,
    OrderIntentEvent,
    ExecutionEvent,
    ParameterUpdatedEvent,
    ORDER_REJECTED,
    OrderTimeoutEvent,
    RiskDecisionEvent,
    MarginUpdateEvent,
    StateRebuiltEvent,
    OrderFilledEvent,
    OrderRejectedEvent,
    OrderCanceledEvent,
    OrderUnknownEvent
}
