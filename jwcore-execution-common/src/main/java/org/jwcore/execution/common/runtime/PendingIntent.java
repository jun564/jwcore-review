package org.jwcore.execution.common.runtime;

import org.jwcore.domain.CanonicalId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PendingIntent(UUID intentId, CanonicalId canonicalId, String accountId, Instant emittedAt, long timeoutThresholdMs) {
    public PendingIntent {
        Objects.requireNonNull(intentId, "intentId cannot be null");
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(emittedAt, "emittedAt cannot be null");
        if (timeoutThresholdMs <= 0L) {
            throw new IllegalArgumentException("timeoutThresholdMs must be positive");
        }
    }
}
