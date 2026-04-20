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

class OrderSubmittedEventTest {
    @Test
    void shouldRejectNullAndBlankFields() {
        final EventEnvelope envelope = envelope();
        final UUID intentId = UUID.randomUUID();
        final CanonicalId canonicalId = CanonicalId.parse("S07:I03:VA07-03:BA01");
        final Instant timestamp = Instant.parse("2026-04-20T12:00:00Z");

        assertThrows(NullPointerException.class, () -> new OrderSubmittedEvent(null, intentId, "BROKER-1", canonicalId, 0.10d, timestamp, envelope));
        assertThrows(IllegalArgumentException.class, () -> new OrderSubmittedEvent("   ", intentId, "BROKER-1", canonicalId, 0.10d, timestamp, envelope));
        assertThrows(NullPointerException.class, () -> new OrderSubmittedEvent("crypto-account", intentId, null, canonicalId, 0.10d, timestamp, envelope));
        assertThrows(IllegalArgumentException.class, () -> new OrderSubmittedEvent("crypto-account", intentId, "   ", canonicalId, 0.10d, timestamp, envelope));
        assertThrows(IllegalArgumentException.class, () -> new OrderSubmittedEvent("crypto-account", intentId, "BROKER-1", canonicalId, 0.0d, timestamp, envelope));
        assertThrows(IllegalArgumentException.class, () -> new OrderSubmittedEvent("crypto-account", intentId, "BROKER-1", canonicalId, -0.1d, timestamp, envelope));
        assertThrows(IllegalArgumentException.class, () -> new OrderSubmittedEvent("crypto-account", intentId, "BROKER-1", canonicalId, Double.NaN, timestamp, envelope));
    }

    @Test
    void shouldRoundTripPayload() {
        final EventEnvelope envelope = envelope();
        final UUID intentId = UUID.randomUUID();
        final Instant timestamp = Instant.parse("2026-04-20T12:00:00Z");
        final CanonicalId canonicalId = CanonicalId.parse("S07:I03:VA07-03:BA01");
        final OrderSubmittedEvent event = new OrderSubmittedEvent(
                "forex-account",
                intentId,
                "BROKER-ORD-42",
                canonicalId,
                0.25d,
                timestamp,
                envelope
        );

        final byte[] payload = event.toPayload();
        assertEquals("forex-account|" + intentId + "|BROKER-ORD-42|S07:I03:VA07-03:BA01|0.25|2026-04-20T12:00:00Z",
                new String(payload, StandardCharsets.UTF_8));

        final OrderSubmittedEvent parsed = OrderSubmittedEvent.fromPayload(payload);
        assertEquals("forex-account", parsed.accountId());
        assertEquals(intentId, parsed.intentId());
        assertEquals("BROKER-ORD-42", parsed.brokerOrderId());
        assertEquals(canonicalId, parsed.canonicalId());
        assertEquals(0.25d, parsed.size());
        assertEquals(timestamp, parsed.timestamp());
        assertNull(parsed.envelope());
    }

    private static EventEnvelope envelope() {
        final byte[] payload = "p".getBytes(StandardCharsets.UTF_8);
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.OrderSubmittedEvent,
                "BROKER-1",
                UUID.randomUUID().toString(),
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                IdempotencyKeys.generate("BROKER-1", EventType.OrderSubmittedEvent, payload),
                1L,
                Instant.parse("2026-04-20T12:00:00Z"),
                (byte) 2,
                payload,
                "domain-test",
                UUID.randomUUID()
        );
    }
}
