package org.jwcore.execution.crypto.runtime;

import java.time.Duration;
import java.util.Objects;

public record ExecutionRuntimeConfig(
        String accountId,
        Duration orderTimeout,
        int marginEmitEveryNCycles,
        long tickIntervalMs,
        int processedEventsCapacity,
        String nodeId) {

    public ExecutionRuntimeConfig {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(orderTimeout, "orderTimeout cannot be null");
        if (orderTimeout.isZero() || orderTimeout.isNegative()) {
            throw new IllegalArgumentException("orderTimeout must be positive");
        }
        if (marginEmitEveryNCycles <= 0) {
            throw new IllegalArgumentException("marginEmitEveryNCycles must be positive");
        }
        if (tickIntervalMs <= 0L) {
            throw new IllegalArgumentException("tickIntervalMs must be positive");
        }
        if (processedEventsCapacity <= 0) {
            throw new IllegalArgumentException("processedEventsCapacity must be positive");
        }
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
    }
}
