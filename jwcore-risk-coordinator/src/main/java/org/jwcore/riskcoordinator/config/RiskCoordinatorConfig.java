package org.jwcore.riskcoordinator.config;

import java.math.BigDecimal;
import java.util.Objects;

public record RiskCoordinatorConfig(BigDecimal safeThreshold,
                                    BigDecimal haltThreshold,
                                    long tickIntervalMs,
                                    int receivedCapacity,
                                    String nodeId) {
    public RiskCoordinatorConfig {
        Objects.requireNonNull(safeThreshold, "safeThreshold cannot be null");
        Objects.requireNonNull(haltThreshold, "haltThreshold cannot be null");
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
        if (safeThreshold.signum() <= 0) {
            throw new IllegalArgumentException("safeThreshold must be positive");
        }
        if (haltThreshold.signum() <= 0) {
            throw new IllegalArgumentException("haltThreshold must be positive");
        }
        if (haltThreshold.compareTo(safeThreshold) < 0) {
            throw new IllegalArgumentException("haltThreshold must be >= safeThreshold");
        }
        if (tickIntervalMs <= 0L) {
            throw new IllegalArgumentException("tickIntervalMs must be positive");
        }
        if (receivedCapacity <= 0) {
            throw new IllegalArgumentException("receivedCapacity must be positive");
        }
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId cannot be blank");
        }
    }
}
