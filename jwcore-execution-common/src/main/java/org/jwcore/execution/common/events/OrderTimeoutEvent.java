package org.jwcore.execution.common.events;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderTimeoutEvent(
        UUID intentId,
        CanonicalId canonicalId,
        String accountId,
        long timeoutThresholdMs,
        Instant intentEmittedAt,
        Instant timeoutTriggeredAt,
        EventEnvelope envelope) {

    public OrderTimeoutEvent {
        Objects.requireNonNull(intentId, "intentId cannot be null");
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(intentEmittedAt, "intentEmittedAt cannot be null");
        Objects.requireNonNull(timeoutTriggeredAt, "timeoutTriggeredAt cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");
        if (timeoutThresholdMs <= 0L) {
            throw new IllegalArgumentException("timeoutThresholdMs must be positive");
        }
    }
}
