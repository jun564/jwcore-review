package org.jwcore.execution.forex.runtime;

import java.time.Duration;
import java.util.Objects;

public record ExecutionRuntimeConfig(
        String accountId,
        Duration executionTimeout,
        Duration brokerTimeout,
        int marginEmitEveryNCycles,
        long tickIntervalMs,
        int processedEventsCapacity,
        String nodeId) {

    public ExecutionRuntimeConfig {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(executionTimeout, "executionTimeout cannot be null");
        Objects.requireNonNull(brokerTimeout, "brokerTimeout cannot be null");
        if (executionTimeout.isZero() || executionTimeout.isNegative()) {
            throw new IllegalArgumentException("executionTimeout must be positive");
        }
        if (brokerTimeout.isZero() || brokerTimeout.isNegative()) {
            throw new IllegalArgumentException("brokerTimeout must be positive");
        }
        if (brokerTimeout.minus(executionTimeout).isNegative()) {
            throw new IllegalArgumentException("brokerTimeout must be greater or equal to executionTimeout");
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
