package org.jwcore.domain.events;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
        final var event = new OrderCanceledEvent("acc-1", "intent-1", "BROKER-1", canonicalId(), new BigDecimal("0.5"), "USER_CANCEL", timestamp(), envelope());
        assertEquals("acc-1", event.accountId());
    }

    @Test
    void shouldRejectBlankAccountId() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderCanceledEvent(" ", "intent-1", "BROKER-1", canonicalId(), new BigDecimal("0.5"), "r", timestamp(), envelope()));
    }

    @Test
    void shouldAllowEmptyReason() {
        final var event = new OrderCanceledEvent("acc-1", "intent-1", "BROKER-1", canonicalId(), new BigDecimal("0.5"), "", timestamp(), envelope());
        assertEquals("", event.reason());
    }

    @Test
    void shouldRejectNullReason() {
        assertThrows(NullPointerException.class,
                () -> new OrderCanceledEvent("acc-1", "intent-1", "BROKER-1", canonicalId(), new BigDecimal("0.5"), null, timestamp(), envelope()));
    }

    @Test
    void shouldSerializeAndDeserializePayloadWithEmptyReason() {
        final var event = new OrderCanceledEvent("acc-1", "intent-1", "BROKER-1", canonicalId(), new BigDecimal("0.5"), "", timestamp(), envelope());
        final String payload = new String(event.toPayload(), StandardCharsets.UTF_8);
        assertTrue(payload.contains("|0.5||"));

        final var parsed = OrderCanceledEvent.fromPayload(event.toPayload());
        assertEquals("", parsed.reason());
        assertNull(parsed.envelope());
    }

    @Test
    void shouldSerializeAndDeserializePayloadWithReason() {
        final var event = new OrderCanceledEvent("acc-1", "intent-1", "BROKER-1", canonicalId(), new BigDecimal("0.5"), "BROKER_CANCEL", timestamp(), envelope());
        final var parsed = OrderCanceledEvent.fromPayload(event.toPayload());
        assertEquals(event.accountId(), parsed.accountId());
        assertEquals(event.intentId(), parsed.intentId());
        assertEquals(event.brokerOrderId(), parsed.brokerOrderId());
        assertEquals(event.canonicalId(), parsed.canonicalId());
        assertEquals(event.size(), parsed.size());
        assertEquals(event.reason(), parsed.reason());
        assertEquals(event.timestamp(), parsed.timestamp());
    }

    @Test
    void shouldThrowReadableExceptionOnMalformedPayload() {
        final var exception = assertThrows(IllegalArgumentException.class,
                () -> OrderCanceledEvent.fromPayload("a|b|c".getBytes(StandardCharsets.UTF_8)));
        assertTrue(exception.getMessage().contains("OrderCanceledEvent"));
        assertTrue(exception.getMessage().contains("expected 7 fields"));
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
                "intent-1",
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
