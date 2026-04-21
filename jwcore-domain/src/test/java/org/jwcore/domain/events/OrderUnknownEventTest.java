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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderUnknownEventTest {

    @Test
    void shouldRejectBlankAccountId() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderUnknownEvent(" ", "intent-1", "BROKER_TIMEOUT", timestamp(), envelope()));
    }

    @Test
    void shouldSerializeAccountIdInPayload() {
        final var event = new OrderUnknownEvent("acc-1", "intent-1", "BROKER_TIMEOUT", timestamp(), envelope());
        final String payload = new String(event.toPayload(), StandardCharsets.UTF_8);
        assertTrue(payload.startsWith("acc-1|intent-1|BROKER_TIMEOUT|"));
    }

    @Test
    void shouldRejectLegacyPayloadFormatWithoutAccountId() {
        final var ex = assertThrows(IllegalArgumentException.class,
                () -> OrderUnknownEvent.fromPayload("intent-1|BROKER_TIMEOUT|2026-04-21T10:00:00Z".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ex.getMessage().contains("OrderUnknownEvent"));
        assertTrue(ex.getMessage().contains("expected 4 fields"));
    }

    @Test
    void shouldSerializeAndDeserializePayload() {
        final var event = new OrderUnknownEvent("acc-1", "intent-1", "BROKER_TIMEOUT", timestamp(), envelope());
        final var parsed = OrderUnknownEvent.fromPayload(event.toPayload());
        assertEquals(event.accountId(), parsed.accountId());
        assertEquals(event.orderIntentId(), parsed.orderIntentId());
        assertEquals(event.reason(), parsed.reason());
        assertEquals(event.timestamp(), parsed.timestamp());
    }

    @Test
    void shouldThrowReadableExceptionOnMalformedPayload() {
        final var exception = assertThrows(IllegalArgumentException.class,
                () -> OrderUnknownEvent.fromPayload("a|b".getBytes(StandardCharsets.UTF_8)));
        assertTrue(exception.getMessage().contains("OrderUnknownEvent"));
        assertTrue(exception.getMessage().contains("expected 4 fields"));
    }

    private static Instant timestamp() {
        return Instant.parse("2026-04-21T10:00:00Z");
    }

    private static EventEnvelope envelope() {
        final byte[] payload = "p".getBytes(StandardCharsets.UTF_8);
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.OrderUnknownEvent,
                "BROKER-1",
                "intent-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                IdempotencyKeys.generate("BROKER-1", EventType.OrderUnknownEvent, payload),
                1L,
                timestamp(),
                (byte) 2,
                payload,
                "domain-test",
                UUID.randomUUID()
        );
    }
}
