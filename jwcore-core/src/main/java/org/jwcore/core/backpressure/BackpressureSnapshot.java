package org.jwcore.core.backpressure;

import java.util.List;
import java.util.Objects;

public record BackpressureSnapshot(double ramUtilization, List<TailerLag> tailers) {
    public BackpressureSnapshot {
        if (!Double.isFinite(ramUtilization) || ramUtilization < 0.0 || ramUtilization > 1.0) {
            throw new IllegalArgumentException("ramUtilization must be between 0.0 and 1.0");
        }
        Objects.requireNonNull(tailers, "tailers cannot be null");
        tailers = List.copyOf(tailers);
    }
}
