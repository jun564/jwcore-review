package org.jwcore.riskcoordinator.tailer;

import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskCoordinatorTailerTest {
    @Test
    void shouldRebuildAllEventsFromJournalUntilEof() {
        final var business = new InMemoryEventJournal();
        final var market = new InMemoryEventJournal();
        business.append(event(EventType.MarginUpdateEvent));
        market.append(event(EventType.OrderTimeoutEvent));
        final var tailer = new RiskCoordinatorTailer(business, market);
        final List<EventEnvelope> consumed = new ArrayList<>();

        tailer.rebuild(consumed::add);

        assertEquals(2, consumed.size());
    }

    @Test
    void shouldPollOnlyNewEventsAfterRebuild() {
        final var business = new InMemoryEventJournal();
        final var market = new InMemoryEventJournal();
        final EventEnvelope first = event(EventType.MarginUpdateEvent);
        business.append(first);
        final var tailer = new RiskCoordinatorTailer(business, market);
        tailer.rebuild(e -> { });

        final EventEnvelope second = event(EventType.OrderTimeoutEvent);
        market.append(second);

        final List<EventEnvelope> consumed = new ArrayList<>();
        tailer.pollSince(consumed::add);

        assertEquals(List.of(second), consumed);
    }

    @Test
    void shouldNotBlockWhenNoNewEvents() {
        final var business = new InMemoryEventJournal();
        final var market = new InMemoryEventJournal();
        final var tailer = new RiskCoordinatorTailer(business, market);
        tailer.rebuild(e -> { });

        final List<EventEnvelope> consumed = new ArrayList<>();
        tailer.pollSince(consumed::add);

        assertTrue(consumed.isEmpty());
    }

    @Test
    void shouldNotDoubleCountEventsAcrossRebuildAndPollSince() {
        final var business = new InMemoryEventJournal();
        final var market = new InMemoryEventJournal();
        final EventEnvelope first = event(EventType.MarginUpdateEvent);
        final EventEnvelope second = event(EventType.OrderTimeoutEvent);
        business.append(first);
        market.append(second);

        final var tailer = new RiskCoordinatorTailer(business, market);
        final List<EventEnvelope> rebuild = new ArrayList<>();
        tailer.rebuild(rebuild::add);

        final List<EventEnvelope> polled = new ArrayList<>();
        tailer.pollSince(polled::add);

        assertEquals(2, rebuild.size());
        assertTrue(polled.isEmpty());
    }

    private static EventEnvelope event(final EventType eventType) {
        return new EventEnvelope(UUID.randomUUID(), eventType, null, null, null, "key", 1L, Instant.parse("2026-04-19T08:00:00Z"), (byte) 1, new byte[0]);
    }
}
