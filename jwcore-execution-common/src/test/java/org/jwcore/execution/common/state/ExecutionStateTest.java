package org.jwcore.execution.common.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionStateTest {
    @Test
    void shouldReturnMoreRestrictiveStateForAllPermutations() {
        for (ExecutionState left : ExecutionState.values()) {
            for (ExecutionState right : ExecutionState.values()) {
                assertEquals(expected(left, right), left.moreRestrictive(right), left + " vs " + right);
            }
        }
    }

    private ExecutionState expected(final ExecutionState left, final ExecutionState right) {
        if (left == ExecutionState.KILL || right == ExecutionState.KILL) return ExecutionState.KILL;
        if (left == ExecutionState.HALT || right == ExecutionState.HALT) return ExecutionState.HALT;
        if (left == ExecutionState.SAFE || right == ExecutionState.SAFE) return ExecutionState.SAFE;
        return ExecutionState.RUN;
    }
}
