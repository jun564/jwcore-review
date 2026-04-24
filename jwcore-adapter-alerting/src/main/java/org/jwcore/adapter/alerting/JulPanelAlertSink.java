package org.jwcore.adapter.alerting;

import org.jwcore.domain.events.AlertEvent;

import java.util.logging.Logger;

public final class JulPanelAlertSink implements AlertSink {
    private static final Logger LOGGER = Logger.getLogger(JulPanelAlertSink.class.getName());

    @Override
    public void deliver(final AlertEvent alert) {
        final String msg = "[PANEL_ALERT] severity=%s accountId=%s from=%s to=%s type=%s reason=%s"
                .formatted(alert.severity(), alert.accountId(), alert.transitionFrom(), alert.transitionTo(), alert.alertType(), alert.reason());
        switch (alert.severity()) {
            case INFO -> LOGGER.info(msg);
            case WARNING -> LOGGER.warning(msg);
            case CRITICAL -> LOGGER.severe(msg);
        }
    }

    @Override
    public String name() {
        return "panel-jul";
    }
}
