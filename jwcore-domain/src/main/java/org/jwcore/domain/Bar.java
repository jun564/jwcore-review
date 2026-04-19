package org.jwcore.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public record Bar(
        Instrument instrument,
        Timeframe timeframe,
        double open,
        double high,
        double low,
        double close,
        double volume,
        Instant timestamp) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public Bar {
        Objects.requireNonNull(instrument, "instrument cannot be null");
        Objects.requireNonNull(timeframe, "timeframe cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        if (!Double.isFinite(open) || !Double.isFinite(high) || !Double.isFinite(low)
                || !Double.isFinite(close) || !Double.isFinite(volume)) {
            throw new IllegalArgumentException("bar values must be finite numbers");
        }
        if (open <= 0.0 || high <= 0.0 || low <= 0.0 || close <= 0.0) {
            throw new IllegalArgumentException("OHLC values must be positive");
        }
        if (high < open || high < close || high < low) {
            throw new IllegalArgumentException("high must be the greatest price in the bar");
        }
        if (low > open || low > close || low > high) {
            throw new IllegalArgumentException("low must be the smallest price in the bar");
        }
        if (volume < 0.0) {
            throw new IllegalArgumentException("volume cannot be negative");
        }
    }
}
