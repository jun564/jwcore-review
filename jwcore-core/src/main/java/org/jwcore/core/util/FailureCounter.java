package org.jwcore.core.util;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.events.EventProcessingFailedEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Liczy proby przetwarzania per failedEventId.
 * Source of truth: journal. Cache LRU jako akcelerator.
 * ADR-017 Faza 2.
 */
public final class FailureCounter {

    static final int MAX_CACHE_SIZE = 10_000;
    static final long TTL_MILLIS = 24 * 60 * 60 * 1000L;

    private final IEventJournal journal;
    private final TimeSource timeSource;

    private final Map<UUID, CountEntry> cache = new LinkedHashMap<>(MAX_CACHE_SIZE + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<UUID, CountEntry> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public FailureCounter(final IEventJournal journal) {
        this(journal, System::currentTimeMillis);
    }

    FailureCounter(final IEventJournal journal, final TimeSource timeSource) {
        this.journal = Objects.requireNonNull(journal, "journal cannot be null");
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource cannot be null");
    }

    public synchronized int nextAttemptNumber(final UUID failedEventId) {
        Objects.requireNonNull(failedEventId, "failedEventId cannot be null");

        final long now = timeSource.nowMillis();
        final CountEntry entry = cache.get(failedEventId);
        if (entry != null && (now - entry.timestamp) < TTL_MILLIS) {
            entry.count++;
            entry.timestamp = now;
            return entry.count;
        }

        final int countFromJournal = countInJournal(failedEventId);
        final int next = countFromJournal + 1;
        cache.put(failedEventId, new CountEntry(next, now));
        return next;
    }

    synchronized void clearCacheForTests() {
        cache.clear();
    }

    synchronized int cacheSizeForTests() {
        return cache.size();
    }

    private int countInJournal(final UUID failedEventId) {
        int count = 0;
        for (final EventEnvelope envelope : journal.readAfterSequence(0)) {
            if (envelope.eventType() != EventType.EventProcessingFailedEvent) {
                continue;
            }
            final EventProcessingFailedEvent failure = deserializeFailure(envelope);
            if (failure != null && failedEventId.equals(failure.failedEventId())) {
                count++;
            }
        }
        return count;
    }

    private EventProcessingFailedEvent deserializeFailure(final EventEnvelope envelope) {
        try {
            return EventProcessingFailedEvent.fromPayload(envelope.payload(), envelope.payloadVersion());
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    private static final class CountEntry {
        private int count;
        private long timestamp;

        private CountEntry(final int count, final long timestamp) {
            this.count = count;
            this.timestamp = timestamp;
        }
    }

    @FunctionalInterface
    interface TimeSource {
        long nowMillis();
    }
}
