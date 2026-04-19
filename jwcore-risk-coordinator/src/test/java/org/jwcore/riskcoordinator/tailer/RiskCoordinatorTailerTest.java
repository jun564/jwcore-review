package org.jwcore.riskcoordinator.tailer;

import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskCoordinatorTailerTest {
    @Test
    void shouldStartReceiveEventsAndClose() {
        final var business = new InMemoryEventJournal();
        final var market = new InMemoryEventJournal();
        final var tailer = new RiskCoordinatorTailer(business, market);

        tailer.start();
        assertTrue(tailer.started());

        business.append(event(EventType.MarginUpdateEvent));
        market.append(event(EventType.OrderTimeoutEvent));

        assertEquals(2, tailer.received().size());
        tailer.close();
        assertFalse(tailer.started());
    }

    private static EventEnvelope event(final EventType eventType) {
        return new EventEnvelope(UUID.randomUUID(), eventType, null, null, null, "key", 1L, Instant.parse("2026-04-19T08:00:00Z"), (byte) 1, new byte[0]);
    }
}
