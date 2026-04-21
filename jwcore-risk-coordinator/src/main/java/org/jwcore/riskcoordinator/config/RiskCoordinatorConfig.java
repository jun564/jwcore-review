package org.jwcore.riskcoordinator.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record RiskCoordinatorConfig(BigDecimal safeThreshold,
                                    BigDecimal haltThreshold,
                                    long tickIntervalMs,
                                    String nodeId,
                                    List<String> monitoredAccounts) {
    public RiskCoordinatorConfig {
        Objects.requireNonNull(safeThreshold, "safeThreshold cannot be null");
        Objects.requireNonNull(haltThreshold, "haltThreshold cannot be null");
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
        Objects.requireNonNull(monitoredAccounts, "monitoredAccounts cannot be null");
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
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId cannot be blank");
        }
        if (monitoredAccounts.isEmpty()) {
            throw new IllegalArgumentException("monitoredAccounts cannot be empty");
        }
        for (final String account : monitoredAccounts) {
            if (account == null || account.isBlank()) {
                throw new IllegalArgumentException("monitoredAccounts cannot contain blank values");
            }
        }
        monitoredAccounts = List.copyOf(monitoredAccounts);
    }
}
