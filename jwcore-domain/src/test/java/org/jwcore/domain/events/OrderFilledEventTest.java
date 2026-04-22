package org.jwcore.domain.events;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.jwcore.domain.OrderSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderFilledEventTest {

    @Test
    void shouldCreateValidEvent() {
        final var event = validEvent(null);
        assertEquals("order-1", event.orderId());
    }

    @Test
    void shouldAllowNullBrokerOrderId() {
        final var event = validEvent(null);
        assertNull(event.brokerOrderId());
    }

    @Test
    void shouldRejectBlankBrokerOrderId() {
        assertThrows(IllegalArgumentException.class, () -> validEvent(" "));
    }

    @Test
    void shouldValidatePositiveFields() {
        assertThrows(IllegalArgumentException.class, () -> new OrderFilledEvent("order-1", null, canonicalId(), OrderSide.BUY,
                BigDecimal.ZERO, new BigDecimal("1"), BigDecimal.ZERO, timestamp(), BigDecimal.ZERO, envelope()));
        assertThrows(IllegalArgumentException.class, () -> new OrderFilledEvent("order-1", null, canonicalId(), OrderSide.BUY,
                new BigDecimal("1"), BigDecimal.ZERO, BigDecimal.ZERO, timestamp(), BigDecimal.ZERO, envelope()));
        assertThrows(IllegalArgumentException.class, () -> new OrderFilledEvent("order-1", null, canonicalId(), OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("-0.01"), timestamp(), BigDecimal.ZERO, envelope()));
        assertThrows(IllegalArgumentException.class, () -> new OrderFilledEvent("order-1", null, canonicalId(), OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("1"), BigDecimal.ZERO, timestamp(), new BigDecimal("-0.01"), envelope()));
    }

    @Test
    void shouldSerializeAndDeserializePayload() {
        final var event = validEvent("BROKER-1");
        final var parsed = OrderFilledEvent.fromPayload(event.toPayload());

        assertEquals(event.orderId(), parsed.orderId());
        assertEquals(event.brokerOrderId(), parsed.brokerOrderId());
        assertEquals(event.canonicalId(), parsed.canonicalId());
        assertEquals(event.side(), parsed.side());
        assertEquals(event.filledQuantity(), parsed.filledQuantity());
        assertEquals(event.averagePrice(), parsed.averagePrice());
        assertEquals(event.commission(), parsed.commission());
        assertEquals(event.timestampFilled(), parsed.timestampFilled());
        assertEquals(event.remainingQuantity(), parsed.remainingQuantity());
        assertNull(parsed.envelope());
    }

    @Test
    void shouldSerializeBigDecimalWithoutScientificNotation() {
        final var event = validEvent("BROKER-1");
        final String payload = new String(event.toPayload(), StandardCharsets.UTF_8);
        assertTrue(payload.contains("|0.10000000|"));
    }

    @Test
    void shouldThrowReadableExceptionOnMalformedPayload() {
        final var exception = assertThrows(IllegalArgumentException.class,
                () -> OrderFilledEvent.fromPayload("a|b|c".getBytes(StandardCharsets.UTF_8)));
        assertTrue(exception.getMessage().contains("OrderFilledEvent"));
        assertTrue(exception.getMessage().contains("expected 9 fields"));
    }

    private static OrderFilledEvent validEvent(final String brokerOrderId) {
        return new OrderFilledEvent(
                "order-1",
                brokerOrderId,
                canonicalId(),
                OrderSide.BUY,
                new BigDecimal("0.10000000"),
                new BigDecimal("123.45"),
                new BigDecimal("0.05"),
                timestamp(),
                BigDecimal.ZERO,
                envelope()
        );
    }

    private static Instant timestamp() {
        return Instant.parse("2026-04-21T10:00:00Z");
    }

    private static CanonicalId canonicalId() {
        return CanonicalId.parse("S07:I03:VA07-03:BA01");
    }

    private static EventEnvelope envelope() {
        final byte[] payload = "p".getBytes(StandardCharsets.UTF_8);
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.OrderFilledEvent,
                "BROKER-1",
                "order-1",
                canonicalId(),
                IdempotencyKeys.generate("BROKER-1", EventType.OrderFilledEvent, payload),
                1L,
                timestamp(),
                (byte) 2,
                payload,
                "domain-test",
                UUID.randomUUID()
        );
    }
}
