package org.jwcore.core.ports;

import org.jwcore.domain.EventEnvelope;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

public interface IEventJournal {
    /**
     * Appends event to journal and assigns domain sequence carried in payload ({@code EventEnvelope.timestampMono}).
     * Invariant: sequence is strictly increasing ({@code next > prev}) but continuity is not guaranteed.
     * Gaps are allowed when write fails after sequence assignment, therefore consumers must use {@code >}
     * comparisons and must not assume {@code next == prev + 1}.
     */
    long append(EventEnvelope envelope);

    List<EventEnvelope> read(Instant fromInclusive, Instant toExclusive);

    /**
     * Returns highest assigned sequence.
     * This value may differ from sequence of last durably persisted event when gaps are present.
     */
    long currentSequence();

    /**
     * Reads events having payload sequence ({@code EventEnvelope.timestampMono}) strictly greater than provided value.
     * Filtering is based on event payload sequence, not physical position/index in the journal.
     */
    List<EventEnvelope> readAfterSequence(long sequence);

    TailSubscription tail(Consumer<EventEnvelope> consumer);
}
