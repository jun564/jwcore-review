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

class OrderFilledEventTest {

    @Test
    void shouldCreateValidEvent() {
        final var event = new OrderFilledEvent("acc-1", "intent-1", "BROKER-1", canonicalId(), new BigDecimal("0.10000000"), timestamp(), envelope());
        assertEquals("acc-1", event.accountId());
    }

    @Test
    void shouldRejectBlankAccountId() {
        assertThrows(IllegalArgumentException.class, () -> new OrderFilledEvent(" ", "intent-1", "BROKER-1", canonicalId(), new BigDecimal("0.1"), timestamp(), envelope()));
    }

    @Test
    void shouldRejectBlankIntentId() {
        assertThrows(IllegalArgumentException.class, () -> new OrderFilledEvent("acc-1", " ", "BROKER-1", canonicalId(), new BigDecimal("0.1"), timestamp(), envelope()));
    }

    @Test
    void shouldRejectBlankBrokerOrderId() {
        assertThrows(IllegalArgumentException.class, () -> new OrderFilledEvent("acc-1", "intent-1", " ", canonicalId(), new BigDecimal("0.1"), timestamp(), envelope()));
    }

    @Test
    void shouldRejectNegativeOrZeroSize() {
        assertThrows(IllegalArgumentException.class, () -> new OrderFilledEvent("acc-1", "intent-1", "BROKER-1", canonicalId(), BigDecimal.ZERO, timestamp(), envelope()));
        assertThrows(IllegalArgumentException.class, () -> new OrderFilledEvent("acc-1", "intent-1", "BROKER-1", canonicalId(), new BigDecimal("-0.1"), timestamp(), envelope()));
    }

    @Test
    void shouldSerializeAndDeserializePayload() {
        final var event = new OrderFilledEvent("acc-1", "intent-1", "BROKER-1", canonicalId(), new BigDecimal("0.10000000"), timestamp(), envelope());
        final var parsed = OrderFilledEvent.fromPayload(event.toPayload());

        assertEquals(event.accountId(), parsed.accountId());
        assertEquals(event.intentId(), parsed.intentId());
        assertEquals(event.brokerOrderId(), parsed.brokerOrderId());
        assertEquals(event.canonicalId(), parsed.canonicalId());
        assertEquals(event.size(), parsed.size());
        assertEquals(event.timestamp(), parsed.timestamp());
        assertNull(parsed.envelope());
    }

    @Test
    void shouldSerializeBigDecimalWithoutScientificNotation() {
        final var event = new OrderFilledEvent("acc-1", "intent-1", "BROKER-1", canonicalId(), new BigDecimal("0.00000001"), timestamp(), envelope());
        final String payload = new String(event.toPayload(), StandardCharsets.UTF_8);
        assertTrue(payload.contains("|0.00000001|"));
    }

    @Test
    void shouldThrowReadableExceptionOnMalformedPayload() {
        final var exception = assertThrows(IllegalArgumentException.class,
                () -> OrderFilledEvent.fromPayload("a|b|c".getBytes(StandardCharsets.UTF_8)));
        assertTrue(exception.getMessage().contains("OrderFilledEvent"));
        assertTrue(exception.getMessage().contains("expected 6 fields"));
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
                "intent-1",
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
