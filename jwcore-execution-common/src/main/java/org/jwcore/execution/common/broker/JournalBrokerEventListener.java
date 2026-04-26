package org.jwcore.execution.common.broker;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.domain.EventEnvelope;

import java.util.Objects;

/**
 * Standardowa implementacja BrokerEventListener pisząca eventy brokera do journala.
 * 4D1: synchroniczna (append konczy sie przed powrotem). Async odlozone na pakcze 5.
 *
 * Wyjatki z journala NIE sa lapane — error isolation jest wyzej (RiskCoordinator),
 * nie w listenerze.
 */
public final class JournalBrokerEventListener implements BrokerEventListener {

    private final IEventJournal journal;

    public JournalBrokerEventListener(final IEventJournal journal) {
        this.journal = Objects.requireNonNull(journal, "journal cannot be null");
    }

    @Override
    public void onBrokerEvent(final EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        journal.append(envelope);
    }
}
