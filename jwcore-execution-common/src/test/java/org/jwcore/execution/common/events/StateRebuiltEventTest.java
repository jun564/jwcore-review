package org.jwcore.execution.common.events;

import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StateRebuiltEventTest {
    @Test
    void shouldCreateImmutableStateRebuiltEvent() {
        final EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), EventType.StateRebuiltEvent, null, null, null, "key", 1L, Instant.parse("2026-04-19T08:00:00Z"), (byte) 1, new byte[0]);
        final var discrepancy = new Discrepancy("missing broker order", "expected", "actual", Instant.parse("2026-04-19T08:00:00Z"));
        final var event = new StateRebuiltEvent("crypto", 1, UUID.randomUUID(), Instant.parse("2026-04-19T08:00:00Z"), RebuildType.CLEAN, 5, List.of(discrepancy), envelope);
        assertEquals(1, event.discrepancies().size());
        assertEquals(RebuildType.CLEAN, event.type());
    }
}
