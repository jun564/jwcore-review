package org.jwcore.core.ports;

import org.jwcore.domain.EventEnvelope;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

public interface IEventJournal {
    void append(EventEnvelope envelope);

    List<EventEnvelope> read(Instant fromInclusive, Instant toExclusive);

    TailSubscription tail(Consumer<EventEnvelope> consumer);
}
