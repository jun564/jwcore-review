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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class OrderTimeoutMonitorTest {

    @Test
    void shouldEmitTimeoutEventWhenDeadlineExpires() {
        final ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        final InMemoryJournal journal = new InMemoryJournal();
        final OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofSeconds(5));

        monitor.registerPending(orderIntent("LI-001"));
        timeProvider.advanceMonotonicTime(Duration.ofSeconds(6).toNanos());
        final List<EventEnvelope> timedOut = monitor.scanTimeouts();

        assertEquals(1, timedOut.size());
        assertEquals(EventType.OrderTimeoutEvent, timedOut.getFirst().eventType());
        assertEquals(1, journal.events.size());
        assertEquals(0, monitor.pendingCount());
    }

    @Test
    void shouldCancelPendingIntentWhenTerminalEventArrives() {
        final ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        final InMemoryJournal journal = new InMemoryJournal();
        final OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofSeconds(5));
        monitor.registerPending(orderIntent("LI-002"));
        assertTrue(monitor.markTerminal("LI-002"));
        timeProvider.advanceMonotonicTime(Duration.ofSeconds(6).toNanos());
        assertTrue(monitor.scanTimeouts().isEmpty());
        assertTrue(journal.events.isEmpty());
    }

    @Test
    void shouldHandleConcurrentRegisterTerminalAndScanWithoutDuplicates() throws Exception {
        final ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        final InMemoryJournal journal = new InMemoryJournal();
        final OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofNanos(1));

        final int threads = 4;
        final int iterations = 1000;
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int idx = t;
            executor.submit(() -> {
                await(start);
                for (int i = 0; i < iterations; i++) {
                    final String id = "T" + idx + "-" + i;
                    monitor.registerPending(orderIntent(id));
                    if ((i & 1) == 0) {
                        monitor.markTerminal(id);
                    }
                    monitor.scanTimeouts();
                }
                done.countDown();
            });
        }

        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS));
        executor.shutdownNow();

        final Set<String> uniqueIntentIds = ConcurrentHashMap.newKeySet();
        for (EventEnvelope envelope : journal.events) {
            assertTrue(uniqueIntentIds.add(envelope.localIntentId()), "duplicate timeout for " + envelope.localIntentId());
        }
        assertDoesNotThrow(monitor::scanTimeouts);
    }

    @Test
    void shouldEmitOutsideLockSoMarkTerminalDoesNotBlockOnAppend() throws Exception {
        final ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        final BlockingJournal journal = new BlockingJournal();
        final OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofNanos(1));

        monitor.registerPending(orderIntent("LI-BLOCK"));
        timeProvider.advanceMonotonicTime(10);

        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch scanStarted = new CountDownLatch(1);
        executor.submit(() -> {
            scanStarted.countDown();
            monitor.scanTimeouts();
        });

        assertTrue(scanStarted.await(2, TimeUnit.SECONDS));
        assertTrue(journal.appendEntered.await(2, TimeUnit.SECONDS));

        final long start = System.nanoTime();
        final boolean removed = monitor.markTerminal("other");
        final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertFalse(removed);
        assertTrue(elapsedMs < 200, "markTerminal should not block on append callback");

        journal.releaseAppend.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void shouldHonorMarkTerminalBeforeScan() {
        final ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        final InMemoryJournal journal = new InMemoryJournal();
        final OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofNanos(1));

        monitor.registerPending(orderIntent("LI-NO-EMIT"));
        assertTrue(monitor.markTerminal("LI-NO-EMIT"));
        timeProvider.advanceMonotonicTime(10);

        assertTrue(monitor.scanTimeouts().isEmpty());
        assertTrue(journal.events.stream().noneMatch(e -> "LI-NO-EMIT".equals(e.localIntentId())));
    }

    @Test
    void shouldRejectInvalidConstructorArguments() {
        final InMemoryJournal journal = new InMemoryJournal();
        final ControllableTimeProvider time = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        assertThrows(NullPointerException.class, () -> new OrderTimeoutMonitor(null, journal, Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> new OrderTimeoutMonitor(time, null, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new OrderTimeoutMonitor(time, journal, Duration.ZERO));
    }

    @Test
    void shouldRejectInvalidPendingRegistration() {
        final ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        final InMemoryJournal journal = new InMemoryJournal();
        final OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofSeconds(5));

        assertThrows(NullPointerException.class, () -> monitor.registerPending(null));

        final EventEnvelope wrongType = new EventEnvelope(UUID.randomUUID(), EventType.ExecutionEvent, null, "LI-003",
                CanonicalId.parse("S07:I03:VA07-03:BA01"), "key", 0L, Instant.now(), (byte) 1, new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> monitor.registerPending(wrongType));

        final EventEnvelope blankIntent = new EventEnvelope(UUID.randomUUID(), EventType.OrderIntentEvent, null, "   ",
                CanonicalId.parse("S07:I03:VA07-03:BA01"), "key", 0L, Instant.now(), (byte) 1, new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> monitor.registerPending(blankIntent));
    }

    @Test
    void shouldRejectNullTerminalId() {
        final ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        final InMemoryJournal journal = new InMemoryJournal();
        final OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofSeconds(5));
        assertThrows(NullPointerException.class, () -> monitor.markTerminal(null));
    }

    @Test
    void shouldReturnFalseForUnknownTerminalId() {
        final ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-18T19:00:00Z"));
        final InMemoryJournal journal = new InMemoryJournal();
        final OrderTimeoutMonitor monitor = new OrderTimeoutMonitor(timeProvider, journal, Duration.ofSeconds(5));
        assertFalse(monitor.markTerminal("missing"));
    }

    private static EventEnvelope orderIntent(final String localIntentId) {
        return new EventEnvelope(UUID.randomUUID(), EventType.OrderIntentEvent, null, localIntentId,
                CanonicalId.parse("S07:I03:VA07-03:BA01"), "key", 0L,
                Instant.parse("2026-04-18T19:00:00Z"), (byte) 1, new byte[]{1});
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static final class InMemoryJournal implements IEventJournal {
        private final List<EventEnvelope> events = new CopyOnWriteArrayList<>();
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

    private static final class BlockingJournal extends InMemoryJournal {
        private final CountDownLatch appendEntered = new CountDownLatch(1);
        private final CountDownLatch releaseAppend = new CountDownLatch(1);

        @Override
        public long append(final EventEnvelope envelope) {
            appendEntered.countDown();
            await(releaseAppend);
            return super.append(envelope);
        }
    }
}
