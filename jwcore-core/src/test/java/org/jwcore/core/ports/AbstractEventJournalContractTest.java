package org.jwcore.core.ports;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractEventJournalContractTest {

    protected abstract IEventJournal createJournal() throws Exception;

    @Test
    void shouldAssignStrictlyIncreasingAndUniqueSequence() throws Exception {
        final IEventJournal journal = createJournal();
        try {
            final long first = journal.append(envelope(EventType.ExecutionEvent, "2026-04-20T10:00:00Z"));
            final long second = journal.append(envelope(EventType.ExecutionEvent, "2026-04-20T10:00:01Z"));
            final long third = journal.append(envelope(EventType.MarketDataEvent, "2026-04-20T10:00:02Z"));

            assertTrue(second > first);
            assertTrue(third > second);
            final Set<Long> unique = new HashSet<>(List.of(first, second, third));
            assertEquals(3, unique.size());
            assertEquals(third, journal.currentSequence());
        } finally {
            closeQuietly(journal);
        }
    }

    @Test
    void shouldReadAfterSequenceUsingEnvelopeSequenceOrdering() throws Exception {
        final IEventJournal journal = createJournal();
        try {
            final long first = journal.append(envelope(EventType.ExecutionEvent, "2026-04-20T10:00:00Z"));
            final long second = journal.append(envelope(EventType.MarketDataEvent, "2026-04-20T10:00:01Z"));
            final long third = journal.append(envelope(EventType.ExecutionEvent, "2026-04-20T10:00:02Z"));

            final List<EventEnvelope> result = journal.readAfterSequence(first);
            assertEquals(2, result.size());
            assertTrue(result.get(0).timestampMono() > first);
            assertTrue(result.get(1).timestampMono() > first);
            assertTrue(result.get(1).timestampMono() > result.get(0).timestampMono());
            assertEquals(second, result.get(0).timestampMono());
            assertEquals(third, result.get(1).timestampMono());
        } finally {
            closeQuietly(journal);
        }
    }

    private static EventEnvelope envelope(final EventType type, final String eventTs) {
        return new EventEnvelope(
                UUID.randomUUID(),
                type,
                "BRK-1",
                UUID.randomUUID().toString(),
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "key-1",
                1L,
                Instant.parse(eventTs),
                (byte) 1,
                new byte[]{1, 2, 3},
                "contract-test",
                UUID.randomUUID()
        );
    }

    private static void closeQuietly(final IEventJournal journal) throws Exception {
        if (journal instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
