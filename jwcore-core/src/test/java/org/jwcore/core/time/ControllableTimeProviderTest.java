package org.jwcore.core.time;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ControllableTimeProviderTest {

    @Test
    void shouldAdvanceTimeWithoutSleeping() {
        ControllableTimeProvider provider = new ControllableTimeProvider(100L, Instant.parse("2026-04-18T19:00:00Z"));
        provider.advanceMonotonicTime(50L);
        provider.advanceEventTime(java.time.Duration.ofSeconds(5));
        assertEquals(150L, provider.monotonicTime());
        assertEquals(Instant.parse("2026-04-18T19:00:05Z"), provider.eventTime());
    }

    @Test
    void shouldRejectNegativeInitialMonotonicTime() {
        assertThrows(IllegalArgumentException.class, () -> new ControllableTimeProvider(-1L, Instant.now()));
    }

    @Test
    void shouldRejectNegativeSetMonotonicTime() {
        ControllableTimeProvider provider = new ControllableTimeProvider(0L, Instant.now());
        assertThrows(IllegalArgumentException.class, () -> provider.setMonotonicTime(-1L));
    }
}
