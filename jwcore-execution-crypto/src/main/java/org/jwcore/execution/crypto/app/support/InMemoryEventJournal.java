package org.jwcore.execution.crypto.app.support;

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
    private final List<Consumer<EventEnvelope>> tails = new CopyOnWriteArrayList<>();

    @Override
    public void append(final EventEnvelope envelope) {
        events.add(envelope);
        for (final Consumer<EventEnvelope> tail : tails) {
            tail.accept(envelope);
        }
    }

    @Override
    public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) {
        final List<EventEnvelope> result = new ArrayList<>();
        for (final EventEnvelope event : events) {
            if (!event.timestampEvent().isBefore(fromInclusive) && event.timestampEvent().isBefore(toExclusive)) {
                result.add(event);
            }
        }
        return result;
    }

    @Override
    public TailSubscription tail(final Consumer<EventEnvelope> consumer) {
        tails.add(consumer);
        return () -> tails.remove(consumer);
    }
}
