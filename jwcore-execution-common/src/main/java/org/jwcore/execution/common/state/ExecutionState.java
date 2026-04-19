package org.jwcore.execution.common.state;

public enum ExecutionState {
    RUN(0),
    SAFE(1),
    HALT(2),
    KILL(3);

    private final int severity;

    ExecutionState(final int severity) {
        this.severity = severity;
    }

    public ExecutionState moreRestrictive(final ExecutionState other) {
        return other == null || this.severity >= other.severity ? this : other;
    }

    public boolean canTransitionTo(final ExecutionState target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case RUN -> target == RUN || target == SAFE || target == HALT || target == KILL;
            case SAFE -> target == SAFE || target == RUN || target == HALT || target == KILL;
            case HALT -> target == HALT || target == SAFE || target == KILL;
            case KILL -> target == KILL;
        };
    }
}
