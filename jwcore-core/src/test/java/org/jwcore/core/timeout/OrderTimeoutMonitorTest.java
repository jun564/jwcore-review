package org.jwcore.core.timeout;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.core.time.ControllableTimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class OrderTimeoutMonitorTest {

    @Test
    void shouldEmitTimeoutEventWhenDeadlineExpires() {
        ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        InMemoryJournal journal = new InMemoryJournal();
        OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofSeconds(5));

        EventEnvelope orderIntent = new EventEnvelope(
                UUID.randomUUID(),
                EventType.OrderIntentEvent,
                null,
                "LI-001",
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                "key",
                0L,
                Instant.parse("2026-04-18T19:00:00Z"),
                (byte) 1,
                new byte[]{1});

        monitor.registerPending(orderIntent);
        timeProvider.advanceMonotonicTime(Duration.ofSeconds(6).toNanos());
        List<EventEnvelope> timedOut = monitor.scanTimeouts();

        assertEquals(1, timedOut.size());
        assertEquals(EventType.OrderTimeoutEvent, timedOut.getFirst().eventType());
        assertEquals(1, journal.events.size());
        assertEquals(0, monitor.pendingCount());
    }

    @Test
    void shouldCancelPendingIntentWhenTerminalEventArrives() {
        ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        InMemoryJournal journal = new InMemoryJournal();
        OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofSeconds(5));
        EventEnvelope orderIntent = new EventEnvelope(
                UUID.randomUUID(), EventType.OrderIntentEvent, null, "LI-002",
                CanonicalId.parse("S07:I03:VA07-03:BA01"), "key", 0L, Instant.now(), (byte) 1, new byte[0]);
        monitor.registerPending(orderIntent);
        assertTrue(monitor.markTerminal("LI-002"));
        timeProvider.advanceMonotonicTime(Duration.ofSeconds(6).toNanos());
        assertTrue(monitor.scanTimeouts().isEmpty());
        assertTrue(journal.events.isEmpty());
    }

    private static final class InMemoryJournal implements IEventJournal {
        private final List<EventEnvelope> events = new ArrayList<>();
        private final AtomicLong sequence = new AtomicLong(0L);

        @Override
        public long append(final EventEnvelope envelope) {
            final long next = sequence.incrementAndGet();
            events.add(withSequence(envelope, next));
            return next;
        }

        @Override
        public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) {
            return List.copyOf(events);
        }

        @Override
        public long currentSequence() {
            return sequence.get();
        }

        @Override
        public List<EventEnvelope> readAfterSequence(final long since) {
            return events.stream().filter(e -> e.sequenceNumber() > since)
                    .sorted(Comparator.comparingLong(EventEnvelope::sequenceNumber))
                    .toList();
        }

        @Override
        public TailSubscription tail(final Consumer<EventEnvelope> consumer) {
            return () -> { };
        }

        private static EventEnvelope withSequence(final EventEnvelope envelope, final long seq) {
            return new EventEnvelope(envelope.eventId(), envelope.eventType(), envelope.brokerOrderId(), envelope.localIntentId(),
                    envelope.canonicalId(), envelope.idempotencyKey(), seq, envelope.timestampEvent(), envelope.payloadVersion(),
                    envelope.payload(), envelope.sourceProcessId(), envelope.correlationId());
        }
    }

    @Test
    void shouldRejectInvalidConstructorArguments() {
        InMemoryJournal journal = new InMemoryJournal();
        ControllableTimeProvider time = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        assertThrows(NullPointerException.class, () -> new OrderTimeoutMonitor(null, journal, Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> new OrderTimeoutMonitor(time, null, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new OrderTimeoutMonitor(time, journal, Duration.ZERO));
    }

    @Test
    void shouldRejectInvalidPendingRegistration() {
        ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        InMemoryJournal journal = new InMemoryJournal();
        OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofSeconds(5));

        assertThrows(NullPointerException.class, () -> monitor.registerPending(null));

        EventEnvelope wrongType = new EventEnvelope(UUID.randomUUID(), EventType.ExecutionEvent, null, "LI-003",
                CanonicalId.parse("S07:I03:VA07-03:BA01"), "key", 0L, Instant.now(), (byte) 1, new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> monitor.registerPending(wrongType));

        EventEnvelope blankIntent = new EventEnvelope(UUID.randomUUID(), EventType.OrderIntentEvent, null, "   ",
                CanonicalId.parse("S07:I03:VA07-03:BA01"), "key", 0L, Instant.now(), (byte) 1, new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> monitor.registerPending(blankIntent));
    }

    @Test
    void shouldRejectNullTerminalId() {
        ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        InMemoryJournal journal = new InMemoryJournal();
        OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofSeconds(5));
        assertThrows(NullPointerException.class, () -> monitor.markTerminal(null));
    }

    @Test
    void shouldReturnFalseForUnknownTerminalId() {
        ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        InMemoryJournal journal = new InMemoryJournal();
        OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofSeconds(5));
        assertFalse(monitor.markTerminal("missing"));
    }

}
