package org.jwcore.riskcoordinator.tailer;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.domain.EventEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RiskCoordinatorTailer implements AutoCloseable {
    private final IEventJournal eventsBusinessJournal;
    private final IEventJournal marketDataJournal;
    private final List<EventEnvelope> received = new CopyOnWriteArrayList<>();
    private final List<TailSubscription> subscriptions = new ArrayList<>();
    private volatile boolean started;

    public RiskCoordinatorTailer(final IEventJournal eventsBusinessJournal, final IEventJournal marketDataJournal) {
        this.eventsBusinessJournal = Objects.requireNonNull(eventsBusinessJournal, "eventsBusinessJournal cannot be null");
        this.marketDataJournal = Objects.requireNonNull(marketDataJournal, "marketDataJournal cannot be null");
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        subscriptions.add(eventsBusinessJournal.tail(received::add));
        subscriptions.add(marketDataJournal.tail(received::add));
        started = true;
    }

    public List<EventEnvelope> received() {
        return List.copyOf(received);
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
}
