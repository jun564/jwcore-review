package org.jwcore.domain.events;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

public record OrderCanceledEvent(
        String accountId,
        String intentId,
        String brokerOrderId,
        CanonicalId canonicalId,
        BigDecimal size,
        String reason,
        Instant timestamp,
        EventEnvelope envelope) {

    public OrderCanceledEvent {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(intentId, "intentId cannot be null");
        Objects.requireNonNull(brokerOrderId, "brokerOrderId cannot be null");
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        Objects.requireNonNull(size, "size cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
        if (intentId.isBlank()) {
            throw new IllegalArgumentException("intentId cannot be blank");
        }
        if (brokerOrderId.isBlank()) {
            throw new IllegalArgumentException("brokerOrderId cannot be blank");
        }
        if (size.signum() <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
    }

    public byte[] toPayload() {
        return String.join("|",
                accountId,
                intentId,
                brokerOrderId,
                canonicalId.format(),
                size.toPlainString(),
                reason,
                timestamp.toString()).getBytes(StandardCharsets.UTF_8);
    }

    public static OrderCanceledEvent fromPayload(final byte[] payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        final String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", 7);
        if (parts.length != 7) {
            throw new IllegalArgumentException("Invalid OrderCanceledEvent payload: expected 7 fields");
        }
        return new OrderCanceledEvent(
                parts[0],
                parts[1],
                parts[2],
                CanonicalId.parse(parts[3]),
                new BigDecimal(parts[4]),
                parts[5],
                Instant.parse(parts[6]),
                null
        );
    }
}
