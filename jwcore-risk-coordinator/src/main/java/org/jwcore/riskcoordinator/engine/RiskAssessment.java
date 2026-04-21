package org.jwcore.riskcoordinator.engine;

import org.jwcore.execution.common.state.ExecutionState;

import java.math.BigDecimal;
import java.util.Objects;

public record RiskAssessment(ExecutionState desiredState, BigDecimal totalExposure, String reason) {
    public RiskAssessment {
        Objects.requireNonNull(desiredState, "desiredState cannot be null");
        Objects.requireNonNull(totalExposure, "totalExposure cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
    }
}
