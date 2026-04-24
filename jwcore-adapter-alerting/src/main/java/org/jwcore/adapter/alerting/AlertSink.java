package org.jwcore.adapter.alerting;

import org.jwcore.domain.events.AlertEvent;

public interface AlertSink {
    void deliver(AlertEvent alert);

    String name();
}
