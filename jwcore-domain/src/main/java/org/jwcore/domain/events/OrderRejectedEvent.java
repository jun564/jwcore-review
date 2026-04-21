package org.jwcore.domain.events;

import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.RejectReason;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

public record OrderRejectedEvent(
        String accountId,
        String orderIntentId,
        RejectReason reason,
        Instant timestamp,
        EventEnvelope envelope) {

    public OrderRejectedEvent {
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
        return String.join("|", accountId, orderIntentId, reason.name(), timestamp.toString()).getBytes(StandardCharsets.UTF_8);
    }

    public static OrderRejectedEvent fromPayload(final byte[] payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        final String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid OrderRejectedEvent payload: expected 4 fields");
        }
        return new OrderRejectedEvent(parts[0], parts[1], RejectReason.valueOf(parts[2]), Instant.parse(parts[3]), null);
    }
}
