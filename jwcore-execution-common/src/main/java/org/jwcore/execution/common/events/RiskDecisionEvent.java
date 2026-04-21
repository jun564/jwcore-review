package org.jwcore.execution.common.events;

import org.jwcore.domain.EventEnvelope;
import org.jwcore.execution.common.state.ExecutionState;

import java.util.Objects;

public record RiskDecisionEvent(String accountId, ExecutionState desiredState, String reason, EventEnvelope envelope) {
    public static final String GLOBAL_ACCOUNT = "ALL";

    public RiskDecisionEvent {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(desiredState, "desiredState cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");
    }

    public boolean appliesTo(final String runtimeAccountId) {
        return GLOBAL_ACCOUNT.equals(accountId) || accountId.equals(runtimeAccountId);
    }
}
