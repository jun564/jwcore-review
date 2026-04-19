package org.jwcore.execution.common.state;

import java.util.Objects;

public final class ExecutionStateResolver {
    public ExecutionState resolve(final ExecutionState localDecision, final ExecutionState globalDecision) {
        final ExecutionState local = Objects.requireNonNullElse(localDecision, ExecutionState.RUN);
        final ExecutionState global = Objects.requireNonNullElse(globalDecision, ExecutionState.RUN);
        return local.moreRestrictive(global);
    }
}
