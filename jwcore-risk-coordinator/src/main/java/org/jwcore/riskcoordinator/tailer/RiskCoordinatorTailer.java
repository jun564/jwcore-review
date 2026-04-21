package org.jwcore.riskcoordinator.tailer;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.domain.EventEnvelope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class RiskCoordinatorTailer implements AutoCloseable {
    private final IEventJournal eventsBusinessJournal;
    private final IEventJournal marketDataJournal;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, EventEnvelope> received;
    private final Object receivedLock = new Object();
    private final List<TailSubscription> subscriptions = new ArrayList<>();
    private volatile boolean started;

    public RiskCoordinatorTailer(final IEventJournal eventsBusinessJournal,
                                 final IEventJournal marketDataJournal,
                                 final int receivedCapacity) {
        this.eventsBusinessJournal = Objects.requireNonNull(eventsBusinessJournal, "eventsBusinessJournal cannot be null");
        this.marketDataJournal = Objects.requireNonNull(marketDataJournal, "marketDataJournal cannot be null");
        if (receivedCapacity <= 0) {
            throw new IllegalArgumentException("receivedCapacity must be positive");
        }
        this.received = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<Long, EventEnvelope> eldest) {
                return size() > receivedCapacity;
            }
        };
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        subscriptions.add(eventsBusinessJournal.tail(this::onEnvelope));
        subscriptions.add(marketDataJournal.tail(this::onEnvelope));
        started = true;
    }

    public List<EventEnvelope> received() {
        synchronized (receivedLock) {
            return List.copyOf(received.values());
        }
    }

    public boolean started() {
        return started;
    }

    @Override
    public synchronized void close() {
        for (final TailSubscription subscription : subscriptions) {
            subscription.close();
        }
        subscriptions.clear();
        started = false;
    }

    private void onEnvelope(final EventEnvelope envelope) {
        synchronized (receivedLock) {
            received.put(sequence.incrementAndGet(), envelope);
        }
    }
}
