package org.jwcore.core.backpressure;

import java.util.Objects;

public record TailerLag(String name, TailerRole role, long lagMessages) {
    public TailerLag {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        if (lagMessages < 0L) {
            throw new IllegalArgumentException("lagMessages cannot be negative");
        }
    }
}
