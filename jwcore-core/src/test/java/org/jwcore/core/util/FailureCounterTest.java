package org.jwcore.core.util;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.events.EventProcessingFailedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FailureCounterTest {

    @Test
    void shouldIncrementAttemptAndUseJournalAfterCacheFlush() {
        final RecordingJournal journal = new RecordingJournal();
        final MutableTime time = new MutableTime();
        final FailureCounter counter = new FailureCounter(journal, time::nowMillis);
        final UUID failedEventId = UUID.randomUUID();

        assertEquals(1, counter.nextAttemptNumber(failedEventId));
        assertEquals(2, counter.nextAttemptNumber(failedEventId));

        journal.addFailure(failedEventId, 1);
        journal.addFailure(failedEventId, 2);
        counter.clearCacheForTests();

        assertEquals(3, counter.nextAttemptNumber(failedEventId));
    }

    @Test
    void shouldExpireByTtl() {
        final RecordingJournal journal = new RecordingJournal();
        final MutableTime time = new MutableTime();
        final FailureCounter counter = new FailureCounter(journal, time::nowMillis);
        final UUID failedEventId = UUID.randomUUID();

        assertEquals(1, counter.nextAttemptNumber(failedEventId));
        time.advance(FailureCounter.TTL_MILLIS + 1);
        journal.addFailure(failedEventId, 1);

        assertEquals(2, counter.nextAttemptNumber(failedEventId));
    }

    @Test
    void shouldRespectLruLimit() {
        final RecordingJournal journal = new RecordingJournal();
        final FailureCounter counter = new FailureCounter(journal, System::currentTimeMillis);

        for (int i = 0; i < FailureCounter.MAX_CACHE_SIZE + 500; i++) {
            counter.nextAttemptNumber(UUID.randomUUID());
        }

        assertEquals(FailureCounter.MAX_CACHE_SIZE, counter.cacheSizeForTests());
    }

    private static final class MutableTime {
        private long millis;
        long nowMillis() { return millis; }
        void advance(final long delta) { millis += delta; }
    }

    private static final class RecordingJournal implements IEventJournal {
        private final List<EventEnvelope> events = new ArrayList<>();

        void addFailure(final UUID failedEventId, final int attempt) {
            final EventProcessingFailedEvent event = new EventProcessingFailedEvent(
                    failedEventId, "type", "msg", Instant.now(), attempt, attempt >= 3,
                    "risk-coordinator", null, null,
                    new EventEnvelope(UUID.randomUUID(), EventType.EventProcessingFailedEvent, null, null, null,
                            "k", 0L, Instant.now(), (byte) 3, new byte[0], "test", null)
            );
            events.add(new EventEnvelope(UUID.randomUUID(), EventType.EventProcessingFailedEvent, null, failedEventId.toString(), null,
                    "failed:" + failedEventId + ":" + attempt, 0L, Instant.now(), (byte) 3, event.toPayload(), "test", null));
        }

        @Override
        public long append(final EventEnvelope envelope) { events.add(envelope); return events.size(); }
        @Override
        public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) { return List.of(); }
        @Override
        public long currentSequence() { return events.size(); }
        @Override
        public List<EventEnvelope> readAfterSequence(final long sequence) { return List.copyOf(events); }
        @Override
        public TailSubscription tail(final Consumer<EventEnvelope> consumer) { return () -> {}; }
    }
}
