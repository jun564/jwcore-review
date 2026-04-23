package org.jwcore.riskcoordinator.command;

import org.jwcore.domain.CanonicalId;
import org.jwcore.execution.common.state.ExecutionState;

import java.time.Instant;
import java.util.Objects;

public record RiskStateResetCommand(
        CanonicalId canonicalId,
        ExecutionState targetState,
        String operatorId,
        String reason,
        Instant requestedAt) {

    public RiskStateResetCommand {
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        Objects.requireNonNull(targetState, "targetState cannot be null");
        Objects.requireNonNull(operatorId, "operatorId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(requestedAt, "requestedAt cannot be null");

        if (targetState != ExecutionState.RUN) {
            throw new IllegalArgumentException("targetState must be RUN");
        }
        if (operatorId.isBlank()) {
            throw new IllegalArgumentException("operatorId cannot be blank");
        }
        if (reason.isBlank() || reason.length() < 10) {
            throw new IllegalArgumentException("reason must be non-blank and at least 10 characters");
        }
    }
}
