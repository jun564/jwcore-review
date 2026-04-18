package org.jwcore.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventEnvelopeTest {

    @Test
    void shouldRoundTripSerializeAndDeserialize() {
        EventEnvelope original = new EventEnvelope(
                UUID.randomUUID(),
                EventType.MarketDataEvent,
                "BRK-1",
                "LI-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "key-1",
                123L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 1,
                new byte[]{1, 2, 3}
        );

        EventEnvelope restored = EventEnvelope.deserialize(original.serialize());
        assertEquals(original, restored);
        assertArrayEquals(new byte[]{1, 2, 3}, restored.payload());
        assertEquals(1, restored.payloadVersion());
    }

    @Test
    void shouldDefensivelyCopyPayload() {
        byte[] payload = new byte[]{1, 2, 3};
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), EventType.MarketDataEvent, null, null, null, "key", 1L, Instant.now(), (byte) 1, payload);
        payload[0] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, envelope.payload());
    }

    @Test
    void shouldTreatNullPayloadAsEmpty() {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), EventType.MarketDataEvent, null, null, null, "key", 1L, Instant.now(), (byte) 1, null);
        assertArrayEquals(new byte[0], envelope.payload());
    }

    @Test
    void shouldRejectNullRequiredFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        assertThrows(NullPointerException.class, () ->
                new EventEnvelope(null, EventType.MarketDataEvent, null, null, null, "key", 1L, now, (byte) 1, null));
        assertThrows(NullPointerException.class, () ->
                new EventEnvelope(id, null, null, null, null, "key", 1L, now, (byte) 1, null));
        assertThrows(NullPointerException.class, () ->
                new EventEnvelope(id, EventType.MarketDataEvent, null, null, null, null, 1L, now, (byte) 1, null));
        assertThrows(NullPointerException.class, () ->
                new EventEnvelope(id, EventType.MarketDataEvent, null, null, null, "key", 1L, null, (byte) 1, null));
    }

    @Test
    void shouldRejectNegativeTimestampMono() {
        assertThrows(IllegalArgumentException.class, () ->
                new EventEnvelope(UUID.randomUUID(), EventType.MarketDataEvent, null, null, null, "key", -1L, Instant.now(), (byte) 1, null));
    }

    @Test
    void shouldRejectNegativePayloadVersion() {
        assertThrows(IllegalArgumentException.class, () ->
                new EventEnvelope(UUID.randomUUID(), EventType.MarketDataEvent, null, null, null, "key", 1L, Instant.now(), (byte) -1, null));
    }

    @Test
    void shouldRejectDeserializeNullBytes() {
        assertThrows(NullPointerException.class, () -> EventEnvelope.deserialize(null));
    }

    @Test
    void shouldRejectDeserializeCorruptBytes() {
        assertThrows(IllegalArgumentException.class, () -> EventEnvelope.deserialize(new byte[]{1, 2, 3}));
    }

    @Test
    void shouldHandleNullCanonicalIdInRoundTrip() {
        EventEnvelope original = new EventEnvelope(
                UUID.randomUUID(),
                EventType.MarketDataEvent,
                null,
                "LI-1",
                null,
                "key-1",
                123L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 2,
                new byte[]{4, 5}
        );
        EventEnvelope restored = EventEnvelope.deserialize(original.serialize());
        assertNull(restored.canonicalId());
        assertEquals(original, restored);
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        EventEnvelope first = new EventEnvelope(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                EventType.ExecutionEvent,
                "BRK-1",
                "LI-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "key",
                100L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 1,
                new byte[]{9, 8});
        EventEnvelope same = new EventEnvelope(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                EventType.ExecutionEvent,
                "BRK-1",
                "LI-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "key",
                100L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 1,
                new byte[]{9, 8});
        EventEnvelope different = new EventEnvelope(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174001"),
                EventType.ExecutionEvent,
                "BRK-1",
                "LI-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "key",
                100L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 1,
                new byte[]{9, 8});
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, different);
    }

    @Test
    void shouldBeEqualToItselfAndRejectDifferentTypes() {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                EventType.MarketDataEvent,
                null,
                null,
                null,
                "key",
                1L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 1,
                new byte[0]
        );
        assertEquals(envelope, envelope);
        assertNotEquals(envelope, null);
        assertNotEquals(envelope, "not-envelope");
    }

    @Test
    void shouldRejectDeserializeWithNegativeStringLength() {
        byte[] bytes = new byte[16 + 16 + 4];
        int pos = 0;
        // UUID zeros
        pos = 16;
        // eventType length = -2
        bytes[pos++] = (byte) 0xFF;
        bytes[pos++] = (byte) 0xFF;
        bytes[pos++] = (byte) 0xFF;
        bytes[pos] = (byte) 0xFE;
        assertThrows(IllegalArgumentException.class, () -> EventEnvelope.deserialize(bytes));
    }

    @Test
    void shouldRejectDeserializeWithNegativeByteArrayLength() {
        EventEnvelope valid = new EventEnvelope(
                UUID.randomUUID(),
                EventType.MarketDataEvent,
                null,
                null,
                null,
                "key",
                1L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 1,
                new byte[]{1, 2}
        );
        byte[] bytes = valid.serialize();
        // overwrite final payload length with -1
        int idx = bytes.length - 2 - 4; // payload bytes len 2 + length field 4
        bytes[idx] = (byte) 0xFF;
        bytes[idx + 1] = (byte) 0xFF;
        bytes[idx + 2] = (byte) 0xFF;
        bytes[idx + 3] = (byte) 0xFF;
        assertThrows(IllegalArgumentException.class, () -> EventEnvelope.deserialize(bytes));
    }

}
