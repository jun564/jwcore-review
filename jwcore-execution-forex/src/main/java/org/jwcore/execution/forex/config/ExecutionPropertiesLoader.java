package org.jwcore.execution.forex.config;

import org.jwcore.execution.forex.runtime.ExecutionRuntimeConfig;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

public final class ExecutionPropertiesLoader {
    public ExecutionRuntimeConfig load(final InputStream inputStream, final String defaultAccountId) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream cannot be null");
        final Properties properties = new Properties();
        properties.load(inputStream);

        final String accountId = properties.getProperty("account.id", defaultAccountId);
        final long executionTimeoutMs = parsePositiveLong(properties, "execution.timeout.ms", 5000L);
        final long brokerTimeoutMs = parsePositiveLong(properties, "broker.timeout.ms", 30000L);
        final int marginEveryCycles = parsePositiveInt(properties, "margin.emit.every.cycles", 5);
        final long tickIntervalMs = parsePositiveLong(properties, "tick.interval.ms", 100L);
        final int processedEventsCapacity = parsePositiveInt(properties, "processed.events.capacity", 100000);
        final String nodeId = Objects.requireNonNull(properties.getProperty("nodeId"), "nodeId cannot be null");
        if (nodeId.isBlank()) {
            throw new IllegalStateException("nodeId must not be blank");
        }

        if (brokerTimeoutMs < executionTimeoutMs) {
            throw new IllegalArgumentException("broker.timeout.ms must be >= execution.timeout.ms");
        }

        return new ExecutionRuntimeConfig(accountId, Duration.ofMillis(executionTimeoutMs), Duration.ofMillis(brokerTimeoutMs), marginEveryCycles, tickIntervalMs, processedEventsCapacity, nodeId);
    }

    private static int parsePositiveInt(final Properties properties, final String key, final int defaultValue) {
        final int value = Integer.parseInt(properties.getProperty(key, Integer.toString(defaultValue)));
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return value;
    }

    private static long parsePositiveLong(final Properties properties, final String key, final long defaultValue) {
        final long value = Long.parseLong(properties.getProperty(key, Long.toString(defaultValue)));
        if (value <= 0L) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return value;
    }
}
