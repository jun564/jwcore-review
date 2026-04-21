package org.jwcore.domain;

public enum EventType {
    MarketDataEvent,
    OrderIntentEvent,
    ExecutionEvent,
    ParameterUpdatedEvent,
    OrderTimeoutEvent,
    RiskDecisionEvent,
    MarginUpdateEvent,
    StateRebuiltEvent,
    OrderFilledEvent,
    OrderSubmittedEvent,
    OrderRejectedEvent,
    OrderCanceledEvent,
    OrderUnknownEvent,
    EventProcessingFailedEvent
}
