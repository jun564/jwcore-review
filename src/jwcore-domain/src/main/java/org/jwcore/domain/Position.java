package org.jwcore.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Stub position for future stages.
 */
public record Position(
        String brokerOrderId,
        CanonicalId canonicalId,
        Instrument instrument,
        double volume,
        double averagePrice) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public Position {
        Objects.requireNonNull(brokerOrderId, "brokerOrderId cannot be null");
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        Objects.requireNonNull(instrument, "instrument cannot be null");
        if (!Double.isFinite(volume) || volume <= 0.0) {
            throw new IllegalArgumentException("volume must be a positive finite number");
        }
        if (!Double.isFinite(averagePrice) || averagePrice <= 0.0) {
            throw new IllegalArgumentException("averagePrice must be a positive finite number");
        }
    }
}
