package org.jwcore.domain.events;

import org.jwcore.domain.EventEnvelope;

import java.time.Instant;
import java.util.Objects;

public record OrderUnknownEvent(
        String orderIntentId,
        String reason,
        Instant timestamp,
        EventEnvelope envelope) {

    public OrderUnknownEvent {
        Objects.requireNonNull(orderIntentId, "orderIntentId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");
        if (orderIntentId.isBlank()) {
            throw new IllegalArgumentException("orderIntentId cannot be blank");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
    }
}
