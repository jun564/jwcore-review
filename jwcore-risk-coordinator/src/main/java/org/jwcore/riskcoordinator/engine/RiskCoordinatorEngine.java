package org.jwcore.riskcoordinator.engine;

import org.jwcore.execution.common.state.ExecutionState;

import java.math.BigDecimal;
import java.util.Objects;

public final class RiskCoordinatorEngine {
    private final BigDecimal safeThreshold;
    private final BigDecimal haltThreshold;

    public RiskCoordinatorEngine(final BigDecimal safeThreshold, final BigDecimal haltThreshold) {
        this.safeThreshold = Objects.requireNonNull(safeThreshold, "safeThreshold cannot be null");
        this.haltThreshold = Objects.requireNonNull(haltThreshold, "haltThreshold cannot be null");
        if (haltThreshold.compareTo(safeThreshold) < 0) {
            throw new IllegalArgumentException("haltThreshold must be >= safeThreshold");
        }
    }

    public RiskAssessment evaluate(final BigDecimal cryptoExposure, final BigDecimal forexExposure) {
        Objects.requireNonNull(cryptoExposure, "cryptoExposure cannot be null");
        Objects.requireNonNull(forexExposure, "forexExposure cannot be null");
        final BigDecimal total = cryptoExposure.add(forexExposure);
        if (total.compareTo(haltThreshold) >= 0) {
            return new RiskAssessment(ExecutionState.HALT, total, "total exposure above HALT threshold");
        }
        if (total.compareTo(safeThreshold) >= 0) {
            return new RiskAssessment(ExecutionState.SAFE, total, "total exposure above SAFE threshold");
        }
        return new RiskAssessment(ExecutionState.RUN, total, "total exposure within limits");
    }
}
