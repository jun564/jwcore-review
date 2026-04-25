package org.jwcore.domain.events;

import org.jwcore.domain.EventEnvelope;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventProcessingFailedEvent(
        UUID failedEventId,
        String errorType,
        String errorMessage,
        Instant timestamp,
        int attemptNumber,
        boolean isPermanent,
        String sourceModule,
        String originalEventType,
        String failedAccountId,
        EventEnvelope envelope) {

    public EventProcessingFailedEvent {
        Objects.requireNonNull(failedEventId, "failedEventId cannot be null");
        Objects.requireNonNull(errorType, "errorType cannot be null");
        Objects.requireNonNull(errorMessage, "errorMessage cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(sourceModule, "sourceModule cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        if (sourceModule.isBlank()) {
            throw new IllegalArgumentException("sourceModule cannot be blank");
        }
        if (isPermanent && attemptNumber < 3) {
            throw new IllegalArgumentException("isPermanent requires attemptNumber >= 3");
        }
    }

    public EventProcessingFailedEvent(final UUID failedEventId,
                                      final String errorType,
                                      final String errorMessage,
                                      final Instant timestamp,
                                      final EventEnvelope envelope) {
        this(failedEventId, errorType, errorMessage, timestamp, 1, false, "unknown", null, null, envelope);
    }

    public byte[] toPayload() {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(baos)) {
                out.writeUTF(failedEventId.toString());
                out.writeUTF(errorType);
                out.writeUTF(errorMessage);
                out.writeLong(timestamp.toEpochMilli());
                out.writeInt(attemptNumber);
                out.writeBoolean(isPermanent);
                out.writeUTF(sourceModule);
                out.writeBoolean(originalEventType != null);
                if (originalEventType != null) {
                    out.writeUTF(originalEventType);
                }
                out.writeBoolean(failedAccountId != null);
                if (failedAccountId != null) {
                    out.writeUTF(failedAccountId);
                }
            }
            return baos.toByteArray();
        } catch (final IOException exception) {
            throw new UncheckedIOException("Failed to serialize EventProcessingFailedEvent", exception);
        }
    }

    public static EventProcessingFailedEvent fromPayload(final byte[] payload,
                                                         final byte version,
                                                         final EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        return fromPayload(payload, version).withEnvelope(envelope);
    }

    public static EventProcessingFailedEvent fromPayload(final byte[] payload, final byte version) {
        Objects.requireNonNull(payload, "payload cannot be null");

        return switch (version) {
            case 2 -> fromLegacyV2(payload);
            case 3 -> fromBinaryV3(payload);
            default -> throw new IllegalArgumentException("Unsupported payload version: " + version);
        };
    }

    private static EventProcessingFailedEvent fromLegacyV2(final byte[] payload) {
        final String payloadText = new String(payload, StandardCharsets.UTF_8);
        final String[] parts = payloadText.split("\\|", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid legacy EventProcessingFailedEvent payload");
        }
        return new EventProcessingFailedEvent(
                UUID.fromString(parts[0]),
                parts[1],
                parts[2],
                Instant.parse(parts[3]),
                1,
                false,
                "unknown",
                null,
                null,
                placeholderEnvelope()
        );
    }

    private static EventProcessingFailedEvent fromBinaryV3(final byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            final UUID failedEventId = UUID.fromString(in.readUTF());
            final String errorType = in.readUTF();
            final String errorMessage = in.readUTF();
            final Instant timestamp = Instant.ofEpochMilli(in.readLong());
            final int attemptNumber = in.readInt();
            final boolean isPermanent = in.readBoolean();
            final String sourceModule = in.readUTF();
            final String originalEventType = in.readBoolean() ? in.readUTF() : null;
            final String failedAccountId = in.readBoolean() ? in.readUTF() : null;
            return new EventProcessingFailedEvent(
                    failedEventId,
                    errorType,
                    errorMessage,
                    timestamp,
                    attemptNumber,
                    isPermanent,
                    sourceModule,
                    originalEventType,
                    failedAccountId,
                    placeholderEnvelope()
            );
        } catch (final IOException exception) {
            throw new UncheckedIOException("Failed to deserialize EventProcessingFailedEvent v3", exception);
        }
    }

    private static EventEnvelope placeholderEnvelope() {
        return new EventEnvelope(
                UUID.randomUUID(),
                org.jwcore.domain.EventType.EventProcessingFailedEvent,
                null,
                null,
                null,
                "placeholder",
                0L,
                Instant.EPOCH,
                (byte) 0,
                new byte[0],
                "unknown",
                null
        );
    }

    public EventProcessingFailedEvent withEnvelope(final EventEnvelope replacementEnvelope) {
        return new EventProcessingFailedEvent(
                failedEventId,
                errorType,
                errorMessage,
                timestamp,
                attemptNumber,
                isPermanent,
                sourceModule,
                originalEventType,
                failedAccountId,
                replacementEnvelope
        );
    }
}
