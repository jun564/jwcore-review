package org.jwcore.execution.crypto.risk;

import org.jwcore.execution.common.state.ExecutionState;

public record LocalRiskSnapshot(String accountId, ExecutionState currentState, boolean brokerConnected) { }
