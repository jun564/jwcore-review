package org.jwcore.riskcoordinator.emitter;

import org.jwcore.execution.common.emit.EventEmitter;
import org.jwcore.execution.common.state.ExecutionState;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class RiskDecisionEmitter {
    private final EventEmitter eventEmitter;
    private final BigDecimal safeThreshold;
    private final BigDecimal haltThreshold;
    private final Map<String, ExecutionState> previous = new LinkedHashMap<>();
    private Map<String, BigDecimal> exposureByAccount = Map.of();

    public RiskDecisionEmitter(final EventEmitter eventEmitter,
                               final BigDecimal safeThreshold,
                               final BigDecimal haltThreshold) {
        this.eventEmitter = Objects.requireNonNull(eventEmitter, "eventEmitter cannot be null");
        this.safeThreshold = Objects.requireNonNull(safeThreshold, "safeThreshold cannot be null");
        this.haltThreshold = Objects.requireNonNull(haltThreshold, "haltThreshold cannot be null");
    }

    public void setExposureByAccount(final Map<String, BigDecimal> exposureByAccount) {
        this.exposureByAccount = Map.copyOf(Objects.requireNonNull(exposureByAccount, "exposureByAccount cannot be null"));
    }

    public void emitChanges(final Map<String, ExecutionState> current) {
        Objects.requireNonNull(current, "current cannot be null");
        for (final Map.Entry<String, ExecutionState> entry : current.entrySet()) {
            final String accountId = entry.getKey();
            final ExecutionState newState = entry.getValue();
            if (newState == previous.get(accountId)) {
                continue;
            }
            eventEmitter.emit(eventEmitter.createRiskDecisionEvent(accountId, newState, reasonFor(accountId, newState)).envelope());
        }
        previous.clear();
        previous.putAll(current);
    }

    private String reasonFor(final String accountId, final ExecutionState newState) {
        final BigDecimal exposure = exposureByAccount.getOrDefault(accountId, BigDecimal.ZERO);
        if (newState == ExecutionState.HALT) {
            return "exposure " + exposure.toPlainString() + " >= haltThreshold " + haltThreshold.toPlainString();
        }
        if (newState == ExecutionState.SAFE) {
            return "exposure " + exposure.toPlainString() + " >= safeThreshold " + safeThreshold.toPlainString();
        }
        return "exposure " + exposure.toPlainString() + " < safeThreshold " + safeThreshold.toPlainString();
    }
}
