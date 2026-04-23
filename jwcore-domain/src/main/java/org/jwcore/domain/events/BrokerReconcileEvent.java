package org.jwcore.domain.events;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

public record BrokerReconcileEvent(
        CanonicalId canonicalId,
        DriftClassification classification,
        java.math.BigDecimal localNetPosition,
        java.math.BigDecimal brokerNetPosition,
        int localIntentCount,
        int brokerPendingCount,
        Instant timestampReconciled,
        int reconnectCount,
        EventEnvelope envelope) {

    public BrokerReconcileEvent {
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        Objects.requireNonNull(classification, "classification cannot be null");
        Objects.requireNonNull(timestampReconciled, "timestampReconciled cannot be null");
        if (reconnectCount < 0) {
            throw new IllegalArgumentException("reconnectCount cannot be negative");
        }
    }

    public byte[] toPayload() {
        try {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            BinaryCodec.writeString(output, canonicalId.format());
            BinaryCodec.writeString(output, classification.name());
            BinaryCodec.writeNullableBigDecimal(output, localNetPosition);
            BinaryCodec.writeNullableBigDecimal(output, brokerNetPosition);
            BinaryCodec.writeInt(output, localIntentCount);
            BinaryCodec.writeInt(output, brokerPendingCount);
            BinaryCodec.writeInstant(output, timestampReconciled);
            BinaryCodec.writeInt(output, reconnectCount);
            return output.toByteArray();
        } catch (final IOException exception) {
            throw new IllegalStateException("Unexpected IO error during BrokerReconcileEvent serialization", exception);
        }
    }

    public static BrokerReconcileEvent fromPayload(final byte[] payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        try {
            final ByteArrayInputStream input = new ByteArrayInputStream(payload);
            final CanonicalId canonicalId = CanonicalId.parse(BinaryCodec.readString(input));
            final DriftClassification classification = DriftClassification.valueOf(BinaryCodec.readString(input));
            final java.math.BigDecimal localNetPosition = BinaryCodec.readNullableBigDecimal(input);
            final java.math.BigDecimal brokerNetPosition = BinaryCodec.readNullableBigDecimal(input);
            final int localIntentCount = BinaryCodec.readInt(input);
            final int brokerPendingCount = BinaryCodec.readInt(input);
            final Instant timestampReconciled = BinaryCodec.readInstant(input);
            final int reconnectCount = BinaryCodec.readInt(input);
            return new BrokerReconcileEvent(canonicalId, classification, localNetPosition, brokerNetPosition,
                    localIntentCount, brokerPendingCount, timestampReconciled, reconnectCount, null);
        } catch (final IOException exception) {
            throw new IllegalArgumentException("Invalid BrokerReconcileEvent payload", exception);
        }
    }

    private static final class BinaryCodec {
        private BinaryCodec() {
        }

        private static void writeString(final ByteArrayOutputStream output, final String value) throws IOException {
            final byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            writeInt(output, bytes.length);
            output.write(bytes);
        }

        private static String readString(final ByteArrayInputStream input) throws IOException {
            final int length = readInt(input);
            if (length < 0) {
                throw new IOException("Negative string length");
            }
            return new String(readFixedBytes(input, length), java.nio.charset.StandardCharsets.UTF_8);
        }

        private static void writeNullableBigDecimal(final ByteArrayOutputStream output,
                                                    final java.math.BigDecimal value) throws IOException {
            if (value == null) {
                writeInt(output, -1);
                return;
            }
            writeString(output, value.toPlainString());
        }

        private static java.math.BigDecimal readNullableBigDecimal(final ByteArrayInputStream input) throws IOException {
            final int length = readInt(input);
            if (length == -1) {
                return null;
            }
            if (length < -1) {
                throw new IOException("Negative nullable decimal length");
            }
            final byte[] bytes = readFixedBytes(input, length);
            return new java.math.BigDecimal(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }

        private static void writeInstant(final ByteArrayOutputStream output, final Instant instant) throws IOException {
            writeLong(output, instant.getEpochSecond());
            writeInt(output, instant.getNano());
        }

        private static Instant readInstant(final ByteArrayInputStream input) throws IOException {
            return Instant.ofEpochSecond(readLong(input), readInt(input));
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
