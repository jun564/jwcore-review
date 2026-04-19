package org.jwcore.execution.common.events;

import java.time.Instant;
import java.util.Objects;

public record Discrepancy(String description, String expectedState, String actualState, Instant detectedAt) {
    public Discrepancy {
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(expectedState, "expectedState cannot be null");
        Objects.requireNonNull(actualState, "actualState cannot be null");
        Objects.requireNonNull(detectedAt, "detectedAt cannot be null");
    }
}
