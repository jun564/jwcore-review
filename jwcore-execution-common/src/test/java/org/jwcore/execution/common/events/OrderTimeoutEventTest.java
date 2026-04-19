package org.jwcore.execution.common.events;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderTimeoutEventTest {
    @Test
    void shouldExposeFields() {
        final UUID intentId = UUID.randomUUID();
        final EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), EventType.OrderTimeoutEvent, null, intentId.toString(),
                CanonicalId.parse("S07:I03:VA07-03:BA01"), "key", 1L, Instant.parse("2026-04-19T08:00:10Z"), (byte) 1, new byte[0]);
        final OrderTimeoutEvent event = new OrderTimeoutEvent(intentId, CanonicalId.parse("S07:I03:VA07-03:BA01"), "crypto", 30000L,
                Instant.parse("2026-04-19T08:00:00Z"), Instant.parse("2026-04-19T08:00:10Z"), envelope);
        assertEquals(intentId, event.intentId());
        assertEquals(30000L, event.timeoutThresholdMs());
    }
}
