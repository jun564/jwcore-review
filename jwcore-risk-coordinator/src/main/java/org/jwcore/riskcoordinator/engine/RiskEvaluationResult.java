package org.jwcore.riskcoordinator.engine;

import org.jwcore.domain.EventEnvelope;

import java.util.Objects;
import java.util.Optional;

public record RiskEvaluationResult(
        Optional<EventEnvelope> decisionEnvelope,
        Optional<EventEnvelope> alertEnvelope) {

    public RiskEvaluationResult {
        Objects.requireNonNull(decisionEnvelope, "decisionEnvelope cannot be null");
        Objects.requireNonNull(alertEnvelope, "alertEnvelope cannot be null");
    }

    public static RiskEvaluationResult empty() {
        return new RiskEvaluationResult(Optional.empty(), Optional.empty());
    }

    public boolean isEmpty() {
        return decisionEnvelope.isEmpty() && alertEnvelope.isEmpty();
    }
}
