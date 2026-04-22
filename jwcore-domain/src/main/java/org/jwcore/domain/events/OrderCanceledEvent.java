package org.jwcore.domain.events;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record OrderCanceledEvent(
        String orderId,
        String brokerOrderId,
        CanonicalId canonicalId,
        String reason,
        Instant timestampCanceled,
        EventEnvelope envelope) {

    private static final Set<String> ALLOWED_REASONS = Set.of("USER_REQUEST", "TIMEOUT", "REJECTED", "EXPIRED");

    public OrderCanceledEvent {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(timestampCanceled, "timestampCanceled cannot be null");

        if (orderId.isBlank()) {
            throw new IllegalArgumentException("orderId cannot be blank");
        }
        if (brokerOrderId != null && brokerOrderId.isBlank()) {
            throw new IllegalArgumentException("brokerOrderId cannot be blank when provided");
        }
        if (!ALLOWED_REASONS.contains(reason)) {
            // TODO: Replace String reason with dedicated OrderCancelReason enum in full lifecycle model.
            throw new IllegalArgumentException("Unsupported cancellation reason: " + reason);
        }
    }

    public byte[] toPayload() {
        return String.join("|",
                orderId,
                brokerOrderId == null ? "" : brokerOrderId,
                canonicalId.format(),
                reason,
                timestampCanceled.toString())
                .getBytes(StandardCharsets.UTF_8);
    }



    // backward-compatible accessors used by older modules.
    public String accountId() {
        return canonicalId.format();
    }

    public String intentId() {
        return orderId;
    }

    public Instant timestamp() {
        return timestampCanceled;
    }

    public java.math.BigDecimal size() {
        return java.math.BigDecimal.ZERO;
    }
    public static OrderCanceledEvent fromPayload(final byte[] payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        final String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", 5);
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid OrderCanceledEvent payload: expected 5 fields");
        }
        final String brokerOrderId = parts[1].isEmpty() ? null : parts[1];
        return new OrderCanceledEvent(
                parts[0],
                brokerOrderId,
                CanonicalId.parse(parts[2]),
                parts[3],
                Instant.parse(parts[4]),
                null
        );
    }
}
