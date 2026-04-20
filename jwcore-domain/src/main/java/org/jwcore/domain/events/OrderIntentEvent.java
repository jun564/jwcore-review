package org.jwcore.domain.events;

import org.jwcore.domain.Instrument;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record OrderIntentEvent(String accountId, Instrument instrument, double size) {
    public OrderIntentEvent {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(instrument, "instrument cannot be null");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
        if (!Double.isFinite(size) || size <= 0.0d) {
            throw new IllegalArgumentException("size must be positive finite");
        }
    }

    public byte[] toPayload() {
        return String.join("|", accountId, instrument.symbol(), Double.toString(size)).getBytes(StandardCharsets.UTF_8);
    }

    public static OrderIntentEvent fromPayload(final byte[] payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        final String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid OrderIntentEvent payload");
        }
        return new OrderIntentEvent(parts[0], new Instrument(parts[1]), Double.parseDouble(parts[2]));
    }
}
