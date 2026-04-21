package org.jwcore.riskcoordinator.tailer;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.domain.EventEnvelope;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class RiskCoordinatorTailer implements AutoCloseable {
    private static final Instant BEGIN = Instant.EPOCH;
    private static final Instant END = Instant.parse("9999-12-31T23:59:59Z");

    private final IEventJournal eventsBusinessJournal;
    private final IEventJournal marketDataJournal;
    private final Set<java.util.UUID> processedEventIds = new HashSet<>();

    public RiskCoordinatorTailer(final IEventJournal eventsBusinessJournal,
                                 final IEventJournal marketDataJournal) {
        this.eventsBusinessJournal = Objects.requireNonNull(eventsBusinessJournal, "eventsBusinessJournal cannot be null");
        this.marketDataJournal = Objects.requireNonNull(marketDataJournal, "marketDataJournal cannot be null");
    }

    public void rebuild(final Consumer<EventEnvelope> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        processedEventIds.clear();
        consumeAllUnseen(consumer);
    }

    public void pollSince(final Consumer<EventEnvelope> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        consumeAllUnseen(consumer);
    }

    @Override
    public void close() {
        // no resources in in-memory implementation
    }

    private void consumeAllUnseen(final Consumer<EventEnvelope> consumer) {
        final List<EventEnvelope> business = eventsBusinessJournal.read(BEGIN, END);
        final List<EventEnvelope> market = marketDataJournal.read(BEGIN, END);
        for (final EventEnvelope envelope : business) {
            if (processedEventIds.add(envelope.eventId())) {
                consumer.accept(envelope);
            }
        }
        for (final EventEnvelope envelope : market) {
            if (processedEventIds.add(envelope.eventId())) {
                consumer.accept(envelope);
            }
        }
    }
}
