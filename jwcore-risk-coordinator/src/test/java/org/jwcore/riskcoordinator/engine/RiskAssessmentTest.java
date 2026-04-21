package org.jwcore.riskcoordinator.engine;

import org.jwcore.execution.common.state.ExecutionState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RiskAssessmentTest {

    @Test
    void shouldCreateWithAllFields() {
        final var assessment = new RiskAssessment(ExecutionState.RUN, new BigDecimal("123.45"), "test reason");
        assertEquals(ExecutionState.RUN, assessment.desiredState());
        assertEquals(new BigDecimal("123.45"), assessment.totalExposure());
        assertEquals("test reason", assessment.reason());
    }

    @Test
    void shouldThrowWhenDesiredStateIsNull() {
        assertThrows(NullPointerException.class,
                () -> new RiskAssessment(null, new BigDecimal("10"), "reason"));
    }

    @Test
    void shouldThrowWhenTotalExposureIsNull() {
        assertThrows(NullPointerException.class,
                () -> new RiskAssessment(ExecutionState.RUN, null, "reason"));
    }

    @Test
    void shouldThrowWhenReasonIsNull() {
        assertThrows(NullPointerException.class,
                () -> new RiskAssessment(ExecutionState.RUN, new BigDecimal("10"), null));
    }

    @Test
    void shouldImplementEqualsAndHashCodeForIdenticalInstances() {
        final var a = new RiskAssessment(ExecutionState.SAFE, new BigDecimal("50"), "same reason");
        final var b = new RiskAssessment(ExecutionState.SAFE, new BigDecimal("50"), "same reason");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void shouldImplementToString() {
        final var assessment = new RiskAssessment(ExecutionState.HALT, new BigDecimal("200"), "halt reason");
        assertNotNull(assessment.toString());
        assertTrue(assessment.toString().contains("HALT"));
    }
}
