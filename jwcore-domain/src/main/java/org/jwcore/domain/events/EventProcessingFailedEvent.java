package org.jwcore.domain.events;

import org.jwcore.domain.EventEnvelope;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventProcessingFailedEvent(
        UUID failedEventId,
        String errorType,
        String errorMessage,
        Instant timestamp,
        EventEnvelope envelope) {

    public EventProcessingFailedEvent {
        Objects.requireNonNull(failedEventId, "failedEventId cannot be null");
        Objects.requireNonNull(errorType, "errorType cannot be null");
        Objects.requireNonNull(errorMessage, "errorMessage cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");
    }
}
