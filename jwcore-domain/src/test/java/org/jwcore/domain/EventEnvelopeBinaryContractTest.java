package org.jwcore.domain;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventEnvelopeBinaryContractTest {

    @Test
    void shouldRoundTripWithSourceProcessIdAndCorrelationId() {
        final EventEnvelope original = new EventEnvelope(
                UUID.fromString("123e4567-e89b-42d3-a456-426614174100"),
                EventType.ExecutionEvent,
                "BRK-1",
                "LI-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "key-1",
                100L,
                Instant.parse("2026-04-19T09:00:00Z"),
                (byte) 2,
                new byte[]{1, 2, 3},
                "crypto-execution-node-1",
                UUID.fromString("123e4567-e89b-42d3-a456-426614174101")
        );

        final EventEnvelope restored = EventEnvelope.deserialize(original.serialize());

        assertEquals(original, restored);
    }

    @Test
    void shouldDeserializeLegacyBinaryWithoutSourceProcessAndCorrelation() throws IOException {
        final byte[] legacyBytes = serializeLegacyEnvelope();

        final EventEnvelope restored = assertDoesNotThrow(() -> EventEnvelope.deserialize(legacyBytes));

        assertNull(restored.sourceProcessId());
        assertNull(restored.correlationId());
        assertEquals(EventType.ExecutionEvent, restored.eventType());
        assertEquals("LI-1", restored.localIntentId());
    }

    @Test
    void shouldRoundTripWithNullSourceProcessIdAndNullCorrelationId() {
        final EventEnvelope original = new EventEnvelope(
                UUID.fromString("123e4567-e89b-42d3-a456-426614174110"),
                EventType.OrderIntentEvent,
                null,
                "LI-2",
                null,
                "key-2",
                101L,
                Instant.parse("2026-04-19T09:01:00Z"),
                (byte) 2,
                new byte[]{7, 8},
                null,
                null
        );

        final EventEnvelope restored = EventEnvelope.deserialize(original.serialize());

        assertEquals(original, restored);
        assertNull(restored.sourceProcessId());
        assertNull(restored.correlationId());
    }

    private static byte[] serializeLegacyEnvelope() throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeUuid(output, UUID.fromString("123e4567-e89b-42d3-a456-426614174120"));
        writeString(output, EventType.ExecutionEvent.name());
        writeNullableString(output, null);
        writeNullableString(output, "LI-1");
        writeNullableString(output, null);
        writeString(output, "legacy-key");
        writeLong(output, 50L);
        writeInstant(output, Instant.parse("2026-04-19T08:55:00Z"));
        writeByte(output, (byte) 1);
        writeByteArray(output, new byte[]{4, 5, 6});
        return output.toByteArray();
    }

    private static void writeUuid(final ByteArrayOutputStream output, final UUID value) throws IOException {
        writeLong(output, value.getMostSignificantBits());
        writeLong(output, value.getLeastSignificantBits());
    }

    private static void writeString(final ByteArrayOutputStream output, final String value) throws IOException {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeInt(output, bytes.length);
        output.write(bytes);
    }

    private static void writeNullableString(final ByteArrayOutputStream output, final String value) throws IOException {
        if (value == null) {
            writeInt(output, -1);
            return;
        }
        writeString(output, value);
    }

    private static void writeInstant(final ByteArrayOutputStream output, final Instant instant) throws IOException {
        writeLong(output, instant.getEpochSecond());
        writeInt(output, instant.getNano());
    }

    private static void writeByte(final ByteArrayOutputStream output, final byte value) throws IOException {
        output.write(value);
    }

    private static void writeByteArray(final ByteArrayOutputStream output, final byte[] bytes) throws IOException {
        writeInt(output, bytes.length);
        output.write(bytes);
    }

    private static void writeInt(final ByteArrayOutputStream output, final int value) throws IOException {
        output.write((value >>> 24) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }

    private static void writeLong(final ByteArrayOutputStream output, final long value) throws IOException {
        for (int shift = 56; shift >= 0; shift -= 8) {
            output.write((int) ((value >>> shift) & 0xFF));
        }
    }
}
