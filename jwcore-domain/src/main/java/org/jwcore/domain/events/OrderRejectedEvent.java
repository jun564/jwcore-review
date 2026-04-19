package org.jwcore.domain.events;

import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.RejectReason;

import java.time.Instant;
import java.util.Objects;

public record OrderRejectedEvent(
        String orderIntentId,
        RejectReason reason,
        Instant timestamp,
        EventEnvelope envelope) {

    public OrderRejectedEvent {
        Objects.requireNonNull(orderIntentId, "orderIntentId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");
    }
}
