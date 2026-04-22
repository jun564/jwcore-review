package org.jwcore.domain.events;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.OrderSide;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

public record OrderFilledEvent(
        String orderId,
        String brokerOrderId,
        CanonicalId canonicalId,
        OrderSide side,
        BigDecimal filledQuantity,
        BigDecimal averagePrice,
        BigDecimal commission,
        Instant timestampFilled,
        BigDecimal remainingQuantity,
        EventEnvelope envelope) {

    public OrderFilledEvent {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        Objects.requireNonNull(side, "side cannot be null");
        Objects.requireNonNull(filledQuantity, "filledQuantity cannot be null");
        Objects.requireNonNull(averagePrice, "averagePrice cannot be null");
        Objects.requireNonNull(commission, "commission cannot be null");
        Objects.requireNonNull(timestampFilled, "timestampFilled cannot be null");
        Objects.requireNonNull(remainingQuantity, "remainingQuantity cannot be null");

        if (orderId.isBlank()) {
            throw new IllegalArgumentException("orderId cannot be blank");
        }
        if (brokerOrderId != null && brokerOrderId.isBlank()) {
            throw new IllegalArgumentException("brokerOrderId cannot be blank when provided");
        }
        if (filledQuantity.signum() <= 0) {
            throw new IllegalArgumentException("filledQuantity must be positive");
        }
        if (averagePrice.signum() <= 0) {
            throw new IllegalArgumentException("averagePrice must be positive");
        }
        if (commission.signum() < 0) {
            throw new IllegalArgumentException("commission cannot be negative");
        }
        if (remainingQuantity.signum() < 0) {
            throw new IllegalArgumentException("remainingQuantity cannot be negative");
        }
    }

    public byte[] toPayload() {
        return String.join("|",
                orderId,
                brokerOrderId == null ? "" : brokerOrderId,
                canonicalId.format(),
                side.name(),
                filledQuantity.toPlainString(),
                averagePrice.toPlainString(),
                commission.toPlainString(),
                timestampFilled.toString(),
                remainingQuantity.toPlainString())
                .getBytes(StandardCharsets.UTF_8);
    }



    // backward-compatible accessors used by older modules.
    public String accountId() {
        return canonicalId.format();
    }

    public String intentId() {
        return orderId;
    }

    public BigDecimal size() {
        return filledQuantity;
    }

    public Instant timestamp() {
        return timestampFilled;
    }
    public static OrderFilledEvent fromPayload(final byte[] payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        final String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", 9);
        if (parts.length != 9) {
            throw new IllegalArgumentException("Invalid OrderFilledEvent payload: expected 9 fields");
        }
        final String brokerOrderId = parts[1].isEmpty() ? null : parts[1];
        return new OrderFilledEvent(
                parts[0],
                brokerOrderId,
                CanonicalId.parse(parts[2]),
                OrderSide.valueOf(parts[3]),
                new BigDecimal(parts[4]),
                new BigDecimal(parts[5]),
                new BigDecimal(parts[6]),
                Instant.parse(parts[7]),
                new BigDecimal(parts[8]),
                null
        );
    }
}
