package org.jwcore.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Normalized instrument identifier. Kept intentionally small and framework-free.
 */
public record Instrument(String symbol) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public Instrument {
        Objects.requireNonNull(symbol, "symbol cannot be null");
        final String normalized = symbol.strip().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("symbol cannot be blank");
        }
        symbol = normalized;
    }
}
