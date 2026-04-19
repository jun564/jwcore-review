package org.jwcore.core.backpressure;

import java.util.Objects;

public record BackpressureDecision(BackpressureLevel level, String reason) {
    public BackpressureDecision {
        Objects.requireNonNull(level, "level cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
    }
}
