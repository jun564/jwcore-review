package org.jwcore.execution.common.registry;

import org.jwcore.core.time.ControllableTimeProvider;
import org.jwcore.execution.common.emit.EventEmitter;
import org.junit.jupiter.api.Test;
import org.jwcore.domain.CanonicalId;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IntentRegistryTest {
    @Test
    void shouldBindLookupRemoveAndEmitTimeout() {
        final var journal = new InMemoryEventJournal();
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var registry = new IntentRegistry(time, new EventEmitter(journal, time));
        final UUID intentId = UUID.randomUUID();
        final CanonicalId canonicalId = CanonicalId.parse("S07:I03:VA07-03:BA01");
        registry.bind(intentId, canonicalId, "crypto", time.eventTime(), 1000L);

        assertEquals(canonicalId, registry.findCanonicalId(intentId).orElseThrow());
        assertEquals(intentId, registry.findIntentId(canonicalId).orElseThrow());

        time.advanceBy(Duration.ofSeconds(2));
        registry.checkTimeouts();
        assertTrue(registry.findCanonicalId(intentId).isEmpty());
        assertEquals(1, journal.all().size());

        registry.bind(intentId, canonicalId, "crypto", time.eventTime(), 1000L);
        assertTrue(registry.remove(intentId));
        assertTrue(registry.findIntentId(canonicalId).isEmpty());
    }
}
