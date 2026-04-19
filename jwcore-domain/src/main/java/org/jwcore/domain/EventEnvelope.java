package org.jwcore.domain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        EventType eventType,
        String brokerOrderId,
        String localIntentId,
        CanonicalId canonicalId,
        String idempotencyKey,
        long timestampMono,
        Instant timestampEvent,
        byte payloadVersion,
        byte[] payload) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        Objects.requireNonNull(timestampEvent, "timestampEvent cannot be null");
        if (timestampMono < 0L) {
            throw new IllegalArgumentException("timestampMono cannot be negative");
        }
        if (payloadVersion < 0) {
            throw new IllegalArgumentException("payloadVersion cannot be negative");
        }
        payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public byte[] serialize() {
        try {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            BinaryCodec.writeUuid(output, eventId);
            BinaryCodec.writeString(output, eventType.name());
            BinaryCodec.writeNullableString(output, brokerOrderId);
            BinaryCodec.writeNullableString(output, localIntentId);
            BinaryCodec.writeNullableString(output, canonicalId == null ? null : canonicalId.format());
            BinaryCodec.writeString(output, idempotencyKey);
            BinaryCodec.writeLong(output, timestampMono);
            BinaryCodec.writeInstant(output, timestampEvent);
            BinaryCodec.writeByte(output, payloadVersion);
            BinaryCodec.writeByteArray(output, payload);
            return output.toByteArray();
        } catch (final IOException exception) {
            throw new IllegalStateException("Unexpected IO error during EventEnvelope serialization", exception);
        }
    }

    public static EventEnvelope deserialize(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        try {
            final ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            final UUID eventId = BinaryCodec.readUuid(input);
            final EventType eventType = EventType.valueOf(BinaryCodec.readString(input));
            final String brokerOrderId = BinaryCodec.readNullableString(input);
            final String localIntentId = BinaryCodec.readNullableString(input);
            final String canonicalIdRaw = BinaryCodec.readNullableString(input);
            final CanonicalId canonicalId = canonicalIdRaw == null ? null : CanonicalId.parse(canonicalIdRaw);
            final String idempotencyKey = BinaryCodec.readString(input);
            final long timestampMono = BinaryCodec.readLong(input);
            final Instant timestampEvent = BinaryCodec.readInstant(input);
            final byte payloadVersion = BinaryCodec.readByte(input);
            final byte[] payload = BinaryCodec.readByteArray(input);
            return new EventEnvelope(
                    eventId,
                    eventType,
                    brokerOrderId,
                    localIntentId,
                    canonicalId,
                    idempotencyKey,
                    timestampMono,
                    timestampEvent,
                    payloadVersion,
                    payload
            );
        } catch (final IOException exception) {
            throw new IllegalArgumentException("Invalid EventEnvelope binary payload", exception);
        }
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EventEnvelope other)) {
            return false;
        }
        return timestampMono == other.timestampMono
                && payloadVersion == other.payloadVersion
                && eventId.equals(other.eventId)
                && eventType == other.eventType
                && Objects.equals(brokerOrderId, other.brokerOrderId)
                && Objects.equals(localIntentId, other.localIntentId)
                && Objects.equals(canonicalId, other.canonicalId)
                && idempotencyKey.equals(other.idempotencyKey)
                && timestampEvent.equals(other.timestampEvent)
                && Arrays.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                eventId,
                eventType,
                brokerOrderId,
                localIntentId,
                canonicalId,
                idempotencyKey,
                timestampMono,
                timestampEvent,
                payloadVersion);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    private static final class BinaryCodec {
        private BinaryCodec() {
        }

        private static void writeUuid(final ByteArrayOutputStream output, final UUID uuid) throws IOException {
            writeLong(output, uuid.getMostSignificantBits());
            writeLong(output, uuid.getLeastSignificantBits());
        }

        private static UUID readUuid(final ByteArrayInputStream input) throws IOException {
            return new UUID(readLong(input), readLong(input));
        }

        private static void writeString(final ByteArrayOutputStream output, final String value) throws IOException {
            final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeInt(output, bytes.length);
            output.write(bytes);
        }

        private static String readString(final ByteArrayInputStream input) throws IOException {
            final int length = readInt(input);
            if (length < 0) {
                throw new IOException("Negative string length");
            }
            return new String(readFixedBytes(input, length), StandardCharsets.UTF_8);
        }

        private static void writeNullableString(final ByteArrayOutputStream output, final String value) throws IOException {
            if (value == null) {
                writeInt(output, -1);
                return;
            }
            writeString(output, value);
        }

        private static String readNullableString(final ByteArrayInputStream input) throws IOException {
            final int length = readInt(input);
            if (length == -1) {
                return null;
            }
            if (length < -1) {
                throw new IOException("Negative nullable string length");
            }
            return new String(readFixedBytes(input, length), StandardCharsets.UTF_8);
        }

        private static void writeInstant(final ByteArrayOutputStream output, final Instant instant) throws IOException {
            writeLong(output, instant.getEpochSecond());
            writeInt(output, instant.getNano());
        }

        private static Instant readInstant(final ByteArrayInputStream input) throws IOException {
            final long epochSecond = readLong(input);
            final int nano = readInt(input);
            return Instant.ofEpochSecond(epochSecond, nano);
        }

        private static void writeByte(final ByteArrayOutputStream output, final byte value) throws IOException {
            output.write(value);
        }

        private static byte readByte(final ByteArrayInputStream input) throws IOException {
            final int value = input.read();
            if (value < 0) {
                throw new IOException("Unexpected end of stream while reading byte");
            }
            return (byte) value;
        }

        private static void writeByteArray(final ByteArrayOutputStream output, final byte[] bytes) throws IOException {
            writeInt(output, bytes.length);
            output.write(bytes);
        }

        private static byte[] readByteArray(final ByteArrayInputStream input) throws IOException {
            final int length = readInt(input);
            if (length < 0) {
                throw new IOException("Negative byte array length");
            }
            return readFixedBytes(input, length);
        }

        private static void writeInt(final ByteArrayOutputStream output, final int value) throws IOException {
            output.write((value >>> 24) & 0xFF);
            output.write((value >>> 16) & 0xFF);
            output.write((value >>> 8) & 0xFF);
            output.write(value & 0xFF);
        }

        private static int readInt(final ByteArrayInputStream input) throws IOException {
            final int b1 = input.read();
            final int b2 = input.read();
            final int b3 = input.read();
            final int b4 = input.read();
            if ((b1 | b2 | b3 | b4) < 0) {
                throw new IOException("Unexpected end of stream while reading int");
            }
            return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
        }

        private static void writeLong(final ByteArrayOutputStream output, final long value) throws IOException {
            for (int shift = 56; shift >= 0; shift -= 8) {
                output.write((int) ((value >>> shift) & 0xFF));
            }
        }

        private static long readLong(final ByteArrayInputStream input) throws IOException {
            long value = 0L;
            for (int shift = 56; shift >= 0; shift -= 8) {
                final int current = input.read();
                if (current < 0) {
                    throw new IOException("Unexpected end of stream while reading long");
                }
                value |= ((long) current) << shift;
            }
            return value;
        }

        private static byte[] readFixedBytes(final ByteArrayInputStream input, final int length) throws IOException {
            final byte[] bytes = input.readNBytes(length);
            if (bytes.length != length) {
                throw new IOException("Unexpected end of stream while reading bytes");
            }
            return bytes;
        }
    }
}
