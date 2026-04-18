package org.jwcore.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Stub domain object for future stages.
 */
public record OrderIntent(
        CanonicalId canonicalId,
        Instrument instrument,
        double volume) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public OrderIntent {
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        Objects.requireNonNull(instrument, "instrument cannot be null");
        if (!Double.isFinite(volume) || volume <= 0.0) {
            throw new IllegalArgumentException("volume must be a positive finite number");
        }
    }
}
