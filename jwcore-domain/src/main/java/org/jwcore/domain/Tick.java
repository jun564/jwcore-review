package org.jwcore.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public record Tick(
        Instrument instrument,
        double bid,
        double ask,
        Instant timestamp) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public Tick {
        Objects.requireNonNull(instrument, "instrument cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        if (!Double.isFinite(bid) || !Double.isFinite(ask)) {
            throw new IllegalArgumentException("bid and ask must be finite numbers");
        }
        if (bid <= 0.0 || ask <= 0.0) {
            throw new IllegalArgumentException("bid and ask must be positive");
        }
        if (ask < bid) {
            throw new IllegalArgumentException("ask cannot be lower than bid");
        }
    }
}
