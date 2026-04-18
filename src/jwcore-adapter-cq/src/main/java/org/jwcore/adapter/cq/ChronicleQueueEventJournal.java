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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ChronicleQueueEventJournal implements IEventJournal, AutoCloseable {
    private static final long TAIL_IDLE_MILLIS = 25L;

    private final ChronicleQueue businessQueue;
    private final ChronicleQueue marketDataQueue;
    private final ExcerptAppender businessAppender;
    private final ExcerptAppender marketDataAppender;

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
    }

    @Override
    public void append(final EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        final ExcerptAppender appender = selectAppender(envelope.eventType());
        final byte[] serialized = envelope.serialize();
        try (DocumentContext documentContext = appender.writingDocument()) {
            final Bytes<?> bytes = documentContext.wire().bytes();
            bytes.writeInt(serialized.length);
            bytes.write(serialized);
        }
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
        envelopes.sort(Comparator
                .comparing(EventEnvelope::timestampEvent)
                .thenComparing(EventEnvelope::eventId));
        return List.copyOf(envelopes);
    }

    @Override
    public TailSubscription tail(final Consumer<EventEnvelope> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        final AtomicBoolean running = new AtomicBoolean(true);
        final CountDownLatch ready = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            final Thread thread = new Thread(r, "jwcore-cq-tailer");
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(() -> {
            final ExcerptTailer businessTailer = businessQueue.createTailer("tail-business").toEnd();
            final ExcerptTailer marketTailer = marketDataQueue.createTailer("tail-market-data").toEnd();
            ready.countDown();
            while (running.get()) {
                final List<EventEnvelope> batch = new ArrayList<>(2);
                readNextInto(batch, businessTailer);
                readNextInto(batch, marketTailer);
                if (batch.isEmpty()) {
                    try {
                        Thread.sleep(TAIL_IDLE_MILLIS);
                    } catch (final InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                }
                batch.sort(Comparator
                        .comparing(EventEnvelope::timestampEvent)
                        .thenComparing(EventEnvelope::eventId));
                batch.forEach(consumer);
            }
        });
        try {
            ready.await();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return () -> {
            running.set(false);
            executor.shutdownNow();
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

    private static void readNextInto(final List<EventEnvelope> target, final ExcerptTailer tailer) {
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

    private ExcerptAppender selectAppender(final EventType eventType) {
        return eventType == EventType.MarketDataEvent ? marketDataAppender : businessAppender;
    }
}
