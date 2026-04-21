package org.jwcore.domain.events;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

public record OrderFilledEvent(
        String accountId,
        String intentId,
        String brokerOrderId,
        CanonicalId canonicalId,
        BigDecimal size,
        Instant timestamp,
        EventEnvelope envelope) {

    public OrderFilledEvent {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(intentId, "intentId cannot be null");
        Objects.requireNonNull(brokerOrderId, "brokerOrderId cannot be null");
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        Objects.requireNonNull(size, "size cannot be null");
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
                timestamp.toString()).getBytes(StandardCharsets.UTF_8);
    }

    public static OrderFilledEvent fromPayload(final byte[] payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        final String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", 6);
        if (parts.length != 6) {
            throw new IllegalArgumentException("Invalid OrderFilledEvent payload: expected 6 fields");
        }
        return new OrderFilledEvent(
                parts[0],
                parts[1],
                parts[2],
                CanonicalId.parse(parts[3]),
                new BigDecimal(parts[4]),
                Instant.parse(parts[5]),
                null
        );
    }
}
