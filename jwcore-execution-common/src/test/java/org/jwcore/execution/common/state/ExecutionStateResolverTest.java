package org.jwcore.execution.common.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionStateResolverTest {
    private final ExecutionStateResolver resolver = new ExecutionStateResolver();

    @Test
    void shouldResolveAllLocalGlobalCombinations() {
        for (ExecutionState local : ExecutionState.values()) {
            for (ExecutionState global : ExecutionState.values()) {
                assertEquals(local.moreRestrictive(global), resolver.resolve(local, global));
            }
        }
    }
}
