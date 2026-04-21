package org.jwcore.riskcoordinator.config;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public final class RiskCoordinatorPropertiesLoader {
    public RiskCoordinatorConfig load(final InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream cannot be null");
        final Properties properties = new Properties();
        properties.load(inputStream);

        final BigDecimal safeThreshold = parsePositiveDecimal(properties, "safe.threshold", "100000");
        final BigDecimal haltThreshold = parsePositiveDecimal(properties, "halt.threshold", "150000");
        final long tickIntervalMs = parsePositiveLong(properties, "tick.interval.ms", 100L);
        final String nodeId = Objects.requireNonNull(properties.getProperty("nodeId"), "nodeId cannot be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId cannot be blank");
        }
        final String monitoredAccountsRaw = properties.getProperty("risk.monitored.accounts");
        if (monitoredAccountsRaw == null) {
            throw new IllegalStateException("risk.monitored.accounts property is required");
        }
        final List<String> monitoredAccounts = Arrays.stream(monitoredAccountsRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (monitoredAccounts.isEmpty()) {
            throw new IllegalStateException("risk.monitored.accounts must contain at least one non-blank account");
        }

        return new RiskCoordinatorConfig(safeThreshold, haltThreshold, tickIntervalMs, nodeId, monitoredAccounts);
    }

    private static BigDecimal parsePositiveDecimal(final Properties properties, final String key, final String defaultValue) {
        final BigDecimal value = new BigDecimal(properties.getProperty(key, defaultValue));
        if (value.signum() <= 0) {
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
