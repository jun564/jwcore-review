package org.jwcore.domain.events;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderSubmittedEvent(
        String accountId,
        UUID intentId,
        String brokerOrderId,
        CanonicalId canonicalId,
        double size,
        Instant timestamp,
        EventEnvelope envelope) {

    public OrderSubmittedEvent {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(intentId, "intentId cannot be null");
        Objects.requireNonNull(brokerOrderId, "brokerOrderId cannot be null");
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
        if (brokerOrderId.isBlank()) {
            throw new IllegalArgumentException("brokerOrderId cannot be blank");
        }
        if (!Double.isFinite(size) || size <= 0.0d) {
            throw new IllegalArgumentException("size must be positive finite");
        }
    }

    public byte[] toPayload() {
        return String.join("|",
                accountId,
                intentId.toString(),
                brokerOrderId,
                canonicalId.format(),
                Double.toString(size),
                timestamp.toString())
                .getBytes(StandardCharsets.UTF_8);
    }

    public static OrderSubmittedEvent fromPayload(final byte[] payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        final String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", 6);
        if (parts.length != 6) {
            throw new IllegalArgumentException("Invalid OrderSubmittedEvent payload");
        }
        return new OrderSubmittedEvent(
                parts[0],
                UUID.fromString(parts[1]),
                parts[2],
                CanonicalId.parse(parts[3]),
                Double.parseDouble(parts[4]),
                Instant.parse(parts[5]),
                null
        );
    }
}
