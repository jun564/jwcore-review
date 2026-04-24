package org.jwcore.adapter.alerting;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.events.AlertEvent;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public final class AlertTailer {
    private static final Logger LOGGER = Logger.getLogger(AlertTailer.class.getName());

    private final IEventJournal journal;
    private final List<AlertSink> sinks;
    private TailSubscription subscription;

    public AlertTailer(final IEventJournal journal, final List<AlertSink> sinks) {
        this.journal = Objects.requireNonNull(journal, "journal cannot be null");
        this.sinks = List.copyOf(Objects.requireNonNull(sinks, "sinks cannot be null"));
    }

    public void start() {
        subscription = journal.tail(this::onEnvelope);
    }

    public void stop() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    private void onEnvelope(final EventEnvelope envelope) {
        if (envelope.eventType() != EventType.AlertEvent) {
            return;
        }
        final AlertEvent alert;
        try {
            alert = AlertEvent.fromPayload(envelope.payload());
        } catch (IOException exception) {
            LOGGER.severe("Failed to deserialize AlertEvent: " + exception);
            return;
        }

        for (AlertSink sink : sinks) {
            try {
                sink.deliver(alert);
            } catch (Exception exception) {
                LOGGER.warning("Sink " + sink.name() + " failed: " + exception);
            }
        }
    }
}
