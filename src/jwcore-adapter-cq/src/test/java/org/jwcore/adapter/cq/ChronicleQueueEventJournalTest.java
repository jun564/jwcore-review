package org.jwcore.adapter.cq;

import org.jwcore.core.ports.TailSubscription;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChronicleQueueEventJournalTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldAppendAndReadAcrossQueuesInOrderedWay() {
        ChronicleQueueJournalConfig config = new ChronicleQueueJournalConfig(tempDir, "events-business", "market-data");
        try (ChronicleQueueEventJournal journal = new ChronicleQueueEventJournal(config)) {
            EventEnvelope business = envelope(EventType.ExecutionEvent, Instant.parse("2026-04-18T19:00:02Z"));
            EventEnvelope market = envelope(EventType.MarketDataEvent, Instant.parse("2026-04-18T19:00:01Z"));
            journal.append(business);
            journal.append(market);
            List<EventEnvelope> events = journal.read(Instant.parse("2026-04-18T19:00:00Z"), Instant.parse("2026-04-18T19:01:00Z"));
            assertEquals(2, events.size());
            assertEquals(EventType.MarketDataEvent, events.get(0).eventType());
            assertEquals(EventType.ExecutionEvent, events.get(1).eventType());
        }
    }

    @Test
    void shouldTailNewEvents() throws Exception {
        ChronicleQueueJournalConfig config = new ChronicleQueueJournalConfig(tempDir, "events-business-tail", "market-data-tail");
        try (ChronicleQueueEventJournal journal = new ChronicleQueueEventJournal(config)) {
            List<EventEnvelope> observed = new ArrayList<>();
            TailSubscription subscription = journal.tail(observed::add);
            try {
                journal.append(envelope(EventType.ExecutionEvent, Instant.parse("2026-04-18T19:00:03Z")));
                Thread.sleep(100L);
                assertFalse(observed.isEmpty());
            } finally {
                subscription.close();
            }
        }
    }


    @Test
    void shouldExcludeEventsBeforeReadRangeStart() {
        ChronicleQueueJournalConfig config = new ChronicleQueueJournalConfig(tempDir, "events-range-filter", "market-range-filter");
        try (ChronicleQueueEventJournal journal = new ChronicleQueueEventJournal(config)) {
            journal.append(envelope(EventType.ExecutionEvent, Instant.parse("2026-04-18T18:59:00Z")));
            journal.append(envelope(EventType.ExecutionEvent, Instant.parse("2026-04-18T19:00:30Z")));
            List<EventEnvelope> events = journal.read(
                    Instant.parse("2026-04-18T19:00:00Z"),
                    Instant.parse("2026-04-18T19:01:00Z"));
            assertEquals(1, events.size());
            assertEquals(Instant.parse("2026-04-18T19:00:30Z"), events.get(0).timestampEvent());
        }
    }

    private static EventEnvelope envelope(final EventType eventType, final Instant when) {
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                "BRK-1",
                "LI-1",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "key-1",
                1L,
                when,
                (byte) 1,
                new byte[]{1, 2, 3}
        );
    }

    @Test
    void shouldRejectInvalidReadRange() {
        ChronicleQueueJournalConfig config = new ChronicleQueueJournalConfig(tempDir, "events-business-range", "market-data-range");
        try (ChronicleQueueEventJournal journal = new ChronicleQueueEventJournal(config)) {
            Instant now = Instant.parse("2026-04-18T19:00:00Z");
            assertThrows(IllegalArgumentException.class, () -> journal.read(now, now));
        }
    }

}
