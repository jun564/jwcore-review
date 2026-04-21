package org.jwcore.execution.common.runtime;

import org.jwcore.core.time.ControllableTimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.execution.common.events.OrderTimeoutEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTimeoutTrackerTest {
    @Test
    void shouldRegisterTriggerAndCleanupTimeout() {
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var tracker = new OrderTimeoutTracker(time);
        final var pending = new PendingIntent(UUID.randomUUID(), CanonicalId.parse("S07:I03:VA07-03:BA01"), "crypto", time.eventTime(), 5000L);
        tracker.register(pending);
        final List<OrderTimeoutEvent> emitted = new ArrayList<>();

        tracker.checkTimeouts(pi -> eventFor(pi, time.eventTime()), emitted::add);
        assertTrue(emitted.isEmpty());
        assertEquals(1, tracker.pendingCount());

        time.advanceBy(Duration.ofSeconds(5));
        tracker.checkTimeouts(pi -> eventFor(pi, time.eventTime()), emitted::add);
        assertEquals(1, emitted.size());
        assertEquals(0, tracker.pendingCount());
    }

    @Test
    void shouldHandleThreeTimeoutsInSingleCycleWithoutConcurrentModification() {
        final var time = new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z"));
        final var tracker = new OrderTimeoutTracker(time);
        tracker.register(new PendingIntent(UUID.randomUUID(), CanonicalId.parse("S07:I03:VA07-03:BA01"), "crypto", time.eventTime(), 5000L));
        tracker.register(new PendingIntent(UUID.randomUUID(), CanonicalId.parse("S07:I04:VA07-04:BA01"), "crypto", time.eventTime(), 5000L));
        tracker.register(new PendingIntent(UUID.randomUUID(), CanonicalId.parse("S07:I05:VA07-05:BA01"), "crypto", time.eventTime(), 5000L));

        final List<OrderTimeoutEvent> emitted = new ArrayList<>();
        time.advanceBy(Duration.ofSeconds(5));

        tracker.checkTimeouts(pi -> eventFor(pi, time.eventTime()), emitted::add);

        assertEquals(3, emitted.size());
        assertEquals(0, tracker.pendingCount());
    }

    private OrderTimeoutEvent eventFor(final PendingIntent pendingIntent, final Instant now) {
        final EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), EventType.OrderTimeoutEvent, null, pendingIntent.intentId().toString(),
                pendingIntent.canonicalId(), "key", 1L, now, (byte) 1, new byte[0]);
        return new OrderTimeoutEvent(pendingIntent.intentId(), pendingIntent.canonicalId(), pendingIntent.accountId(), pendingIntent.timeoutThresholdMs(), pendingIntent.emittedAt(), now, envelope);
    }
}
