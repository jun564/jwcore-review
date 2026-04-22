package org.jwcore.execution.crypto.runtime;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.domain.EventEnvelope;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class InMemoryEventJournal implements IEventJournal {
    private final List<EventEnvelope> events = new CopyOnWriteArrayList<>();
    private final List<Consumer<EventEnvelope>> tails = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong(0L);

    @Override public long append(final EventEnvelope envelope) {
        final long next = sequence.incrementAndGet();
        final EventEnvelope canonical = withSequence(envelope, next);
        events.add(canonical);
        tails.forEach(t -> t.accept(canonical));
        return next;
    }
    @Override public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) {
        return events.stream().filter(e -> !e.timestampEvent().isBefore(fromInclusive) && e.timestampEvent().isBefore(toExclusive)).toList();
    }
    @Override public long currentSequence() { return sequence.get(); }
    @Override public List<EventEnvelope> readAfterSequence(final long since) {
        return events.stream().filter(e -> e.timestampMono() > since).sorted(Comparator.comparingLong(EventEnvelope::timestampMono)).toList();
    }
    @Override public TailSubscription tail(final Consumer<EventEnvelope> consumer) { tails.add(consumer); return () -> tails.remove(consumer); }
    public List<EventEnvelope> all() { return List.copyOf(events); }

    private static EventEnvelope withSequence(final EventEnvelope envelope, final long seq) {
        return new EventEnvelope(envelope.eventId(), envelope.eventType(), envelope.brokerOrderId(), envelope.localIntentId(),
                envelope.canonicalId(), envelope.idempotencyKey(), seq, envelope.timestampEvent(), envelope.payloadVersion(),
                envelope.payload(), envelope.sourceProcessId(), envelope.correlationId());
    }
}
