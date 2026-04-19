package org.jwcore.riskcoordinator.config;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
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
        return new RiskCoordinatorConfig(safeThreshold, haltThreshold, tickIntervalMs);
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
