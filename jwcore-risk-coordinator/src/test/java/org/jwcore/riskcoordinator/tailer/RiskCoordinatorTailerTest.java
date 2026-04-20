package org.jwcore.riskcoordinator.tailer;

import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskCoordinatorTailerTest {
    @Test
    void shouldStartReceiveEventsAndClose() {
        final var business = new InMemoryEventJournal();
        final var market = new InMemoryEventJournal();
        final var tailer = new RiskCoordinatorTailer(business, market, 100);

        tailer.start();
        assertTrue(tailer.started());

        business.append(event(EventType.MarginUpdateEvent));
        market.append(event(EventType.OrderTimeoutEvent));

        assertEquals(2, tailer.received().size());
        tailer.close();
        assertFalse(tailer.started());
    }

    @Test
    void shouldBoundReceivedEnvelopes() {
        final var business = new InMemoryEventJournal();
        final var market = new InMemoryEventJournal();
        final var tailer = new RiskCoordinatorTailer(business, market, 3);

        tailer.start();
        final EventEnvelope first = event(EventType.MarginUpdateEvent);
        final EventEnvelope second = event(EventType.OrderTimeoutEvent);
        final EventEnvelope third = event(EventType.MarginUpdateEvent);
        final EventEnvelope fourth = event(EventType.OrderTimeoutEvent);
        final EventEnvelope fifth = event(EventType.MarginUpdateEvent);

        business.append(first);
        business.append(second);
        market.append(third);
        market.append(fourth);
        business.append(fifth);

        final List<EventEnvelope> received = tailer.received();
        assertEquals(3, received.size());
        assertEquals(List.of(third, fourth, fifth), received);

        tailer.close();
    }

    private static EventEnvelope event(final EventType eventType) {
        return new EventEnvelope(UUID.randomUUID(), eventType, null, null, null, "key", 1L, Instant.parse("2026-04-19T08:00:00Z"), (byte) 1, new byte[0]);
    }
}
