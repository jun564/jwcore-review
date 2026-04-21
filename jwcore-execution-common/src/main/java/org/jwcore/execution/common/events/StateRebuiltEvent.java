package org.jwcore.execution.common.events;

import org.jwcore.domain.EventEnvelope;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record StateRebuiltEvent(
        String accountId,
        int snapshotVersion,
        UUID rebuiltUntilEventId,
        Instant rebuiltUntilTimestamp,
        RebuildType type,
        int eventsReplayed,
        List<Discrepancy> discrepancies,
        EventEnvelope envelope) {

    public StateRebuiltEvent {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(rebuiltUntilEventId, "rebuiltUntilEventId cannot be null");
        Objects.requireNonNull(rebuiltUntilTimestamp, "rebuiltUntilTimestamp cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(discrepancies, "discrepancies cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");
        if (snapshotVersion < 0) {
            throw new IllegalArgumentException("snapshotVersion cannot be negative");
        }
        if (eventsReplayed < 0) {
            throw new IllegalArgumentException("eventsReplayed cannot be negative");
        }
        discrepancies = List.copyOf(discrepancies);
    }
}
