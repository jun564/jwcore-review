package org.jwcore.core.failure;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.time.ITimeProvider;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.events.EventProcessingFailedEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ProcessingFailureEmitter {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 512;
    private static final byte PAYLOAD_VERSION = 3;

    private final IEventJournal journal;
    private final ITimeProvider timeProvider;
    private final String sourceProcessId;

    public ProcessingFailureEmitter(final IEventJournal journal,
                                    final ITimeProvider timeProvider,
                                    final String sourceProcessId) {
        this.journal = Objects.requireNonNull(journal, "journal cannot be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider cannot be null");
        this.sourceProcessId = Objects.requireNonNull(sourceProcessId, "sourceProcessId cannot be null");
        if (sourceProcessId.isBlank()) {
            throw new IllegalArgumentException("sourceProcessId cannot be blank");
        }
    }

    public EventProcessingFailedEvent emit(final UUID failedEventId, final Throwable exception) {
        return emit(failedEventId, exception, 1, "unknown", null, null);
    }

    public EventProcessingFailedEvent emit(final UUID failedEventId,
                                           final Throwable exception,
                                           final int attemptNumber,
                                           final String sourceModule,
                                           final String originalEventType,
                                           final String failedAccountId) {
        Objects.requireNonNull(failedEventId, "failedEventId cannot be null");
        Objects.requireNonNull(exception, "exception cannot be null");

        final Instant now = timeProvider.eventTime();
        final String errorType = exception.getClass().getName();
        final String errorMessage = sanitizeAndTruncate(Objects.toString(exception.getMessage(), ""));
        final boolean isPermanent = attemptNumber >= 3;

        final EventProcessingFailedEvent event = new EventProcessingFailedEvent(
                failedEventId,
                errorType,
                errorMessage,
                now,
                attemptNumber,
                isPermanent,
                sourceModule,
                originalEventType,
                failedAccountId,
                placeholderEnvelope()
        );

        final byte[] payload = event.toPayload();
        final String customIdempotencyKey = "failed:" + failedEventId + ":" + attemptNumber;

        final EventEnvelope failureEnvelope = new EventEnvelope(
                UUID.randomUUID(),
                EventType.EventProcessingFailedEvent,
                null,
                failedEventId.toString(),
                null,
                customIdempotencyKey,
                timeProvider.monotonicTime(),
                now,
                PAYLOAD_VERSION,
                payload,
                sourceProcessId,
                null
        );

        journal.append(failureEnvelope);
        return event.withEnvelope(failureEnvelope);
    }

    private static String sanitizeAndTruncate(final String rawMessage) {
        final String sanitized = rawMessage.replace('|', '/');
        if (sanitized.length() <= ERROR_MESSAGE_MAX_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }

    private static EventEnvelope placeholderEnvelope() {
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.EventProcessingFailedEvent,
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
}
