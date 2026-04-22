package org.jwcore.domain.events;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderCanceledEventTest {

    @Test
    void shouldCreateValidEvent() {
        final var event = new OrderCanceledEvent("order-1", null, canonicalId(), "USER_REQUEST", timestamp(), envelope());
        assertEquals("order-1", event.orderId());
    }

    @Test
    void shouldAcceptNullBrokerOrderIdAndRejectBlank() {
        final var event = new OrderCanceledEvent("order-1", null, canonicalId(), "TIMEOUT", timestamp(), envelope());
        assertNull(event.brokerOrderId());
        assertThrows(IllegalArgumentException.class,
                () -> new OrderCanceledEvent("order-1", " ", canonicalId(), "TIMEOUT", timestamp(), envelope()));
    }

    @Test
    void shouldValidateReasonFromAllowedSet() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderCanceledEvent("order-1", null, canonicalId(), "BROKER_CANCEL", timestamp(), envelope()));
    }

    @Test
    void shouldSerializeAndDeserializePayload() {
        final var event = new OrderCanceledEvent("order-1", "BROKER-1", canonicalId(), "REJECTED", timestamp(), envelope());
        final var parsed = OrderCanceledEvent.fromPayload(event.toPayload());

        assertEquals(event.orderId(), parsed.orderId());
        assertEquals(event.brokerOrderId(), parsed.brokerOrderId());
        assertEquals(event.canonicalId(), parsed.canonicalId());
        assertEquals(event.reason(), parsed.reason());
        assertEquals(event.timestampCanceled(), parsed.timestampCanceled());
        assertNull(parsed.envelope());
    }

    @Test
    void shouldThrowReadableExceptionOnMalformedPayload() {
        final var exception = assertThrows(IllegalArgumentException.class,
                () -> OrderCanceledEvent.fromPayload("a|b|c".getBytes(StandardCharsets.UTF_8)));
        assertTrue(exception.getMessage().contains("OrderCanceledEvent"));
        assertTrue(exception.getMessage().contains("expected 5 fields"));
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
                EventType.OrderCanceledEvent,
                "BROKER-1",
                "order-1",
                canonicalId(),
                IdempotencyKeys.generate("BROKER-1", EventType.OrderCanceledEvent, payload),
                1L,
                timestamp(),
                (byte) 2,
                payload,
                "domain-test",
                UUID.randomUUID()
        );
    }
}
