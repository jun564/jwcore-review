package org.jwcore.execution.crypto.risk;

import org.jwcore.execution.common.state.ExecutionState;

@FunctionalInterface
public interface LocalRiskPolicy {
    ExecutionState evaluate(LocalRiskSnapshot snapshot);
}
