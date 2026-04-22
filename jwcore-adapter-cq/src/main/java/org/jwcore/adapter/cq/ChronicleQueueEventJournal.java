package org.jwcore.adapter.cq;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;
import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;

import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ChronicleQueueEventJournal implements IEventJournal, AutoCloseable {
    private static final long TAIL_IDLE_MILLIS = 25L;
    private static final long LEGACY_NANOS_THRESHOLD = 1_000_000_000_000_000L;

    private final ChronicleQueue businessQueue;
    private final ChronicleQueue marketDataQueue;
    private final ExcerptAppender businessAppender;
    private final ExcerptAppender marketDataAppender;
    private final AtomicLong sequenceCounter;

    public ChronicleQueueEventJournal(final ChronicleQueueJournalConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        try {
            Files.createDirectories(config.businessQueuePath());
            Files.createDirectories(config.marketDataQueuePath());
        } catch (final Exception exception) {
            throw new IllegalStateException("Cannot prepare Chronicle Queue directories", exception);
        }
        this.businessQueue = SingleChronicleQueueBuilder.binary(config.businessQueuePath().toFile())
                .rollCycle(RollCycles.HOURLY)
                .build();
        this.marketDataQueue = SingleChronicleQueueBuilder.binary(config.marketDataQueuePath().toFile())
                .rollCycle(RollCycles.HOURLY)
                .build();
        this.businessAppender = businessQueue.acquireAppender();
        this.marketDataAppender = marketDataQueue.acquireAppender();

        final long maxSequence = Math.max(scanMaxSequence(businessQueue, "business"), scanMaxSequence(marketDataQueue, "market-data"));
        this.sequenceCounter = new AtomicLong(maxSequence);
    }

    @Override
    public synchronized long append(final EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        final ExcerptAppender appender = selectAppender(envelope.eventType());
        final long sequence = sequenceCounter.incrementAndGet();
        final EventEnvelope canonical = withSequence(envelope, sequence);
        final byte[] serialized = canonical.serialize();
        try (DocumentContext documentContext = appender.writingDocument()) {
            final Bytes<?> bytes = documentContext.wire().bytes();
            bytes.writeInt(serialized.length);
            bytes.write(serialized);
        }
        return sequence;
    }

    @Override
    public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) {
        Objects.requireNonNull(fromInclusive, "fromInclusive cannot be null");
        Objects.requireNonNull(toExclusive, "toExclusive cannot be null");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be before toExclusive");
        }

        final List<EventEnvelope> envelopes = new ArrayList<>();
        readQueueInto(envelopes, businessQueue, fromInclusive, toExclusive);
        readQueueInto(envelopes, marketDataQueue, fromInclusive, toExclusive);
        envelopes.sort(Comparator.comparing(EventEnvelope::timestampEvent).thenComparing(EventEnvelope::eventId));
        return List.copyOf(envelopes);
    }

    @Override
    public long currentSequence() {
        return sequenceCounter.get();
    }

    @Override
    public List<EventEnvelope> readAfterSequence(final long sequence) {
        final List<EventEnvelope> envelopes = new ArrayList<>();
        readQueueAfterSequence(envelopes, businessQueue, sequence, "business");
        readQueueAfterSequence(envelopes, marketDataQueue, sequence, "market-data");
        // TODO DŁUG-317: add index/seek by sequence to optimize from O(n) scan to O(log n) or better.
        envelopes.sort(Comparator.comparingLong(EventEnvelope::timestampMono).thenComparing(EventEnvelope::eventId));
        return List.copyOf(envelopes);
    }

    @Override
    public TailSubscription tail(final Consumer<EventEnvelope> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        final AtomicBoolean running = new AtomicBoolean(true);
        final ConcurrentLinkedQueue<EventEnvelope> dispatchQueue = new ConcurrentLinkedQueue<>();

        final ScheduledExecutorService businessExecutor = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "jwcore-cq-business-tailer"));
        final ScheduledExecutorService marketDataExecutor = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "jwcore-cq-market-data-tailer"));
        final ScheduledExecutorService dispatcherExecutor = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "jwcore-cq-dispatcher"));

        final ExcerptTailer businessTailer = businessQueue.createTailer("tail-business");
        if (businessQueue.lastIndex() != -1L) {
            businessTailer.toEnd();
        }
        final ExcerptTailer marketTailer = marketDataQueue.createTailer("tail-market-data");
        if (marketDataQueue.lastIndex() != -1L) {
            marketTailer.toEnd();
        }

        businessExecutor.scheduleWithFixedDelay(() -> pollTailer(running, businessTailer, dispatchQueue), 0L, TAIL_IDLE_MILLIS, TimeUnit.MILLISECONDS);
        marketDataExecutor.scheduleWithFixedDelay(() -> pollTailer(running, marketTailer, dispatchQueue), 0L, TAIL_IDLE_MILLIS, TimeUnit.MILLISECONDS);
        dispatcherExecutor.scheduleWithFixedDelay(() -> dispatchPending(running, dispatchQueue, consumer), 0L, TAIL_IDLE_MILLIS, TimeUnit.MILLISECONDS);

        return () -> {
            running.set(false);
            shutdownExecutor(dispatcherExecutor);
            shutdownExecutor(businessExecutor);
            shutdownExecutor(marketDataExecutor);
        };
    }

    public void flush() {
        businessAppender.sync();
        marketDataAppender.sync();
    }

    @Override
    public void close() {
        flush();
        businessQueue.close();
        marketDataQueue.close();
    }

    private static long scanMaxSequence(final ChronicleQueue queue, final String queueName) {
        final ExcerptTailer tailer = queue.createTailer();
        final long lastIndex = queue.lastIndex();
        long max = 0L;
        while (true) {
            try (DocumentContext documentContext = tailer.readingDocument()) {
                if (!documentContext.isPresent()) {
                    return max;
                }
                final long index = documentContext.index();
                try {
                    final EventEnvelope envelope = readEnvelope(documentContext);
                    if (envelope.timestampMono() > LEGACY_NANOS_THRESHOLD) {
                        throw new IllegalStateException("Legacy journal detected, nie kompatybilny z 3C sequence API");
                    }
                    max = Math.max(max, envelope.timestampMono());
                } catch (final RuntimeException exception) {
                    if (index < lastIndex) {
                        throw new IllegalStateException("Unreadable record detected in the middle of stream for queue "
                                + queueName + " at index " + index, exception);
                    }
                    return max;
                }
            }
        }
    }

    private static void pollTailer(final AtomicBoolean running,
                                   final ExcerptTailer tailer,
                                   final ConcurrentLinkedQueue<EventEnvelope> dispatchQueue) {
        if (!running.get()) {
            return;
        }
        try {
            readNextInto(dispatchQueue, tailer);
        } catch (final Exception ignored) {
        }
    }

    private static void dispatchPending(final AtomicBoolean running,
                                        final ConcurrentLinkedQueue<EventEnvelope> dispatchQueue,
                                        final Consumer<EventEnvelope> consumer) {
        if (!running.get() && dispatchQueue.isEmpty()) {
            return;
        }
        try {
            final List<EventEnvelope> batch = new ArrayList<>();
            EventEnvelope next;
            while ((next = dispatchQueue.poll()) != null) {
                batch.add(next);
            }
            batch.sort(Comparator.comparing(EventEnvelope::timestampEvent).thenComparing(EventEnvelope::eventId));
            batch.forEach(consumer);
        } catch (final Exception ignored) {
        }
    }

    private static void shutdownExecutor(final ScheduledExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (final InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static Thread daemon(final Runnable runnable, final String name) {
        final Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void readQueueInto(final List<EventEnvelope> target,
                                      final ChronicleQueue queue,
                                      final Instant fromInclusive,
                                      final Instant toExclusive) {
        final ExcerptTailer tailer = queue.createTailer();
        while (true) {
            try (DocumentContext documentContext = tailer.readingDocument()) {
                if (!documentContext.isPresent()) {
                    return;
                }
                final EventEnvelope envelope = readEnvelope(documentContext);
                if (!envelope.timestampEvent().isBefore(fromInclusive) && envelope.timestampEvent().isBefore(toExclusive)) {
                    target.add(envelope);
                }
            }
        }
    }

    private static void readQueueAfterSequence(final List<EventEnvelope> target,
                                               final ChronicleQueue queue,
                                               final long sequence,
                                               final String queueName) {
        final ExcerptTailer tailer = queue.createTailer();
        while (true) {
            try (DocumentContext documentContext = tailer.readingDocument()) {
                if (!documentContext.isPresent()) {
                    return;
                }
                final long index = documentContext.index();
                try {
                    final EventEnvelope envelope = readEnvelope(documentContext);
                    if (envelope.timestampMono() > sequence) {
                        target.add(envelope);
                    }
                } catch (final RuntimeException exception) {
                    throw new IllegalStateException("Unreadable record during readAfterSequence for queue "
                            + queueName + " at index " + index, exception);
                }
            }
        }
    }

    private static void readNextInto(final ConcurrentLinkedQueue<EventEnvelope> target, final ExcerptTailer tailer) {
        try (DocumentContext documentContext = tailer.readingDocument()) {
            if (!documentContext.isPresent()) {
                return;
            }
            target.add(readEnvelope(documentContext));
        }
    }

    private static EventEnvelope readEnvelope(final DocumentContext documentContext) {
        final Bytes<?> bytes = documentContext.wire().bytes();
        final int length = bytes.readInt();
        final byte[] payload = new byte[length];
        bytes.read(payload);
        return EventEnvelope.deserialize(payload);
    }

    private static EventEnvelope withSequence(final EventEnvelope envelope, final long sequence) {
        return new EventEnvelope(
                envelope.eventId(),
                envelope.eventType(),
                envelope.brokerOrderId(),
                envelope.localIntentId(),
                envelope.canonicalId(),
                envelope.idempotencyKey(),
                sequence,
                envelope.timestampEvent(),
                envelope.payloadVersion(),
                envelope.payload(),
                envelope.sourceProcessId(),
                envelope.correlationId());
    }

    private ExcerptAppender selectAppender(final EventType eventType) {
        return eventType == EventType.MarketDataEvent ? marketDataAppender : businessAppender;
    }
}
