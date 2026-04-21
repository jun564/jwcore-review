package org.jwcore.domain.events;

import org.jwcore.domain.EventEnvelope;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

public record OrderUnknownEvent(
        String accountId,
        String orderIntentId,
        String reason,
        Instant timestamp,
        EventEnvelope envelope) {

    public OrderUnknownEvent {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(orderIntentId, "orderIntentId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
        if (orderIntentId.isBlank()) {
            throw new IllegalArgumentException("orderIntentId cannot be blank");
        }
    }

    public byte[] toPayload() {
        return String.join("|", accountId, orderIntentId, reason, timestamp.toString()).getBytes(StandardCharsets.UTF_8);
    }

    public static OrderUnknownEvent fromPayload(final byte[] payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        final String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid OrderUnknownEvent payload: expected 4 fields");
        }
        return new OrderUnknownEvent(parts[0], parts[1], parts[2], Instant.parse(parts[3]), null);
    }
}
