package org.jwcore.execution.common.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateMachineTest {

    @Test
    void shouldAllowLegalTransitions() {
        assertTrue(ExecutionState.RUN.canTransitionTo(ExecutionState.SAFE));
        assertTrue(ExecutionState.SAFE.canTransitionTo(ExecutionState.RUN));
        assertTrue(ExecutionState.SAFE.canTransitionTo(ExecutionState.HALT));
        assertTrue(ExecutionState.HALT.canTransitionTo(ExecutionState.RUN));
        assertTrue(ExecutionState.HALT.canTransitionTo(ExecutionState.KILL));
    }

    @Test
    void shouldRejectIllegalTransitions() {
        assertFalse(ExecutionState.HALT.canTransitionTo(ExecutionState.SAFE));
        assertFalse(ExecutionState.KILL.canTransitionTo(ExecutionState.RUN));
        assertFalse(ExecutionState.KILL.canTransitionTo(ExecutionState.SAFE));
        assertFalse(ExecutionState.KILL.canTransitionTo(ExecutionState.HALT));
    }
}
