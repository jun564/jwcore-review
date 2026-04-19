package org.jwcore.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventEnvelopeTest {

    @Test
    void shouldRoundTripSerializeAndDeserializeIncludingSourceProcessAndCorrelation() {
        final UUID eventId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");
        final UUID correlationId = UUID.fromString("123e4567-e89b-42d3-a456-426614174001");
        final EventEnvelope original = new EventEnvelope(
                eventId,
                EventType.MarketDataEvent,
                "BRK-1",
                "LI-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "key-1",
                123L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 2,
                new byte[]{1, 2, 3},
                "exec-crypto-1",
                correlationId
        );

        final EventEnvelope restored = EventEnvelope.deserialize(original.serialize());
        assertEquals(original, restored);
        assertArrayEquals(new byte[]{1, 2, 3}, restored.payload());
        assertEquals("exec-crypto-1", restored.sourceProcessId());
        assertEquals(correlationId, restored.correlationId());
    }

    @Test
    void shouldUseBackwardCompatibleConstructorWithUnknownSourceAndEventIdCorrelation() {
        final UUID eventId = UUID.fromString("123e4567-e89b-42d3-a456-426614174010");
        final EventEnvelope envelope = new EventEnvelope(
                eventId,
                EventType.MarketDataEvent,
                null,
                null,
                null,
                "key",
                1L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 1,
                new byte[]{9}
        );
        assertEquals("unknown", envelope.sourceProcessId());
        assertEquals(eventId, envelope.correlationId());
    }

    @Test
    void shouldDefensivelyCopyPayload() {
        final byte[] payload = new byte[]{1, 2, 3};
        final EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), EventType.MarketDataEvent, null, null, null, "key", 1L,
                Instant.parse("2026-04-18T19:00:00Z"), (byte) 1, payload);
        payload[0] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, envelope.payload());
    }

    @Test
    void shouldTreatNullPayloadAsEmpty() {
        final EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), EventType.MarketDataEvent, null, null, null, "key", 1L,
                Instant.parse("2026-04-18T19:00:00Z"), (byte) 1, null);
        assertArrayEquals(new byte[0], envelope.payload());
    }

    @Test
    void shouldRejectNullRequiredFields() {
        final UUID id = UUID.randomUUID();
        final Instant now = Instant.parse("2026-04-18T19:00:00Z");

        assertThrows(NullPointerException.class, () ->
                new EventEnvelope(null, EventType.MarketDataEvent, null, null, null, "key", 1L, now, (byte) 1, null));
        assertThrows(NullPointerException.class, () ->
                new EventEnvelope(id, null, null, null, null, "key", 1L, now, (byte) 1, null));
        assertThrows(NullPointerException.class, () ->
                new EventEnvelope(id, EventType.MarketDataEvent, null, null, null, null, 1L, now, (byte) 1, null));
        assertThrows(NullPointerException.class, () ->
                new EventEnvelope(id, EventType.MarketDataEvent, null, null, null, "key", 1L, null, (byte) 1, null));
        assertThrows(NullPointerException.class, () ->
                new EventEnvelope(id, EventType.MarketDataEvent, null, null, null, "key", 1L, now, (byte) 1, null, null, id));
        assertThrows(NullPointerException.class, () ->
                new EventEnvelope(id, EventType.MarketDataEvent, null, null, null, "key", 1L, now, (byte) 1, null, "proc", null));
    }

    @Test
    void shouldRejectInvalidFieldValues() {
        final Instant now = Instant.parse("2026-04-18T19:00:00Z");
        assertThrows(IllegalArgumentException.class, () ->
                new EventEnvelope(UUID.randomUUID(), EventType.MarketDataEvent, null, null, null, "key", -1L, now, (byte) 1, null));
        assertThrows(IllegalArgumentException.class, () ->
                new EventEnvelope(UUID.randomUUID(), EventType.MarketDataEvent, null, null, null, "key", 1L, now, (byte) -1, null));
        assertThrows(IllegalArgumentException.class, () ->
                new EventEnvelope(UUID.randomUUID(), EventType.MarketDataEvent, null, null, null, "key", 1L, now, (byte) 1, null, " ", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () ->
                new EventEnvelope(new UUID(1L, 2L), EventType.MarketDataEvent, null, null, null, "key", 1L, now, (byte) 1, null, "proc", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () ->
                new EventEnvelope(UUID.randomUUID(), EventType.MarketDataEvent, null, null, null, "key", 1L, now, (byte) 1, null, "proc", new UUID(1L, 2L)));
    }

    @Test
    void shouldRejectDeserializeNullAndCorruptBytes() {
        assertThrows(NullPointerException.class, () -> EventEnvelope.deserialize(null));
        assertThrows(IllegalArgumentException.class, () -> EventEnvelope.deserialize(new byte[]{1, 2, 3}));
    }

    @Test
    void shouldHandleNullCanonicalIdInRoundTrip() {
        final EventEnvelope original = new EventEnvelope(
                UUID.randomUUID(),
                EventType.MarketDataEvent,
                null,
                "LI-1",
                null,
                "key-1",
                123L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 2,
                new byte[]{4, 5},
                "risk-coordinator-1",
                UUID.randomUUID()
        );
        final EventEnvelope restored = EventEnvelope.deserialize(original.serialize());
        assertNull(restored.canonicalId());
        assertEquals(original, restored);
    }

    @Test
    void shouldImplementEqualsAndHashCodeIncludingNewFields() {
        final UUID correlationId = UUID.fromString("123e4567-e89b-42d3-a456-426614174002");
        final EventEnvelope first = new EventEnvelope(
                UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
                EventType.ExecutionEvent,
                "BRK-1",
                "LI-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "key",
                100L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 2,
                new byte[]{9, 8},
                "exec-crypto-1",
                correlationId);
        final EventEnvelope same = new EventEnvelope(
                UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
                EventType.ExecutionEvent,
                "BRK-1",
                "LI-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "key",
                100L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 2,
                new byte[]{9, 8},
                "exec-crypto-1",
                correlationId);
        final EventEnvelope different = new EventEnvelope(
                UUID.fromString("123e4567-e89b-42d3-a456-426614174001"),
                EventType.ExecutionEvent,
                "BRK-1",
                "LI-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "key",
                100L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 2,
                new byte[]{9, 8},
                "exec-forex-1",
                correlationId);
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, different);
    }
}
