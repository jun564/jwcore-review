package org.jwcore.execution.forex.risk;

import org.jwcore.execution.common.state.ExecutionState;

public record LocalRiskSnapshot(String accountId, ExecutionState currentState, boolean brokerConnected) { }
