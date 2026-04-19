package org.jwcore.execution.common.registry;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.domain.EventEnvelope;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class InMemoryEventJournal implements IEventJournal {
    private final List<EventEnvelope> events = new CopyOnWriteArrayList<>();
    private final List<Consumer<EventEnvelope>> consumers = new CopyOnWriteArrayList<>();

    @Override public void append(final EventEnvelope envelope) { events.add(envelope); consumers.forEach(c -> c.accept(envelope)); }
    @Override public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) {
        return events.stream().filter(e -> !e.timestampEvent().isBefore(fromInclusive) && e.timestampEvent().isBefore(toExclusive)).toList();
    }
    @Override public TailSubscription tail(final Consumer<EventEnvelope> consumer) { consumers.add(consumer); return () -> consumers.remove(consumer); }
    public List<EventEnvelope> all() { return List.copyOf(events); }
}
