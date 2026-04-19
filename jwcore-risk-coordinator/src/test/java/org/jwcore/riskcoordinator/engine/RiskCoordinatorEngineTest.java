package org.jwcore.riskcoordinator.engine;

import org.jwcore.execution.common.state.ExecutionState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskCoordinatorEngineTest {
    @Test
    void shouldEvaluateExposureThresholds() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));
        assertEquals(ExecutionState.RUN, engine.evaluate(new BigDecimal("10"), new BigDecimal("20")).desiredState());
        assertEquals(ExecutionState.SAFE, engine.evaluate(new BigDecimal("80"), new BigDecimal("30")).desiredState());
        assertEquals(ExecutionState.HALT, engine.evaluate(new BigDecimal("100"), new BigDecimal("60")).desiredState());
    }
}
