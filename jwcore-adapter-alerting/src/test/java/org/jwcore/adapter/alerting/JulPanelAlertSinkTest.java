package org.jwcore.adapter.alerting;

import org.jwcore.domain.events.AlertEvent;
import org.jwcore.domain.events.AlertSeverity;
import org.jwcore.domain.events.AlertType;
import org.jwcore.domain.ExecutionState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JulPanelAlertSinkTest {

    @Test
    void shouldLogPanelMarkerAndLevels() {
        final Logger logger = Logger.getLogger(JulPanelAlertSink.class.getName());
        final CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            final JulPanelAlertSink sink = new JulPanelAlertSink();
            sink.deliver(event(AlertSeverity.INFO));
            sink.deliver(event(AlertSeverity.WARNING));
            sink.deliver(event(AlertSeverity.CRITICAL));

            assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("[PANEL_ALERT]")));
            assertEquals(Level.INFO, handler.records.get(0).getLevel());
            assertEquals(Level.WARNING, handler.records.get(1).getLevel());
            assertEquals(Level.SEVERE, handler.records.get(2).getLevel());
        } finally {
            logger.removeHandler(handler);
        }
    }

    private static AlertEvent event(final AlertSeverity severity) {
        return new AlertEvent(UUID.randomUUID(), "a", severity, ExecutionState.RUN, ExecutionState.SAFE, AlertType.STATE_TRANSITION, "reason", List.of(), Instant.now());
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(final LogRecord record) {
            records.add(record);
        }

        @Override public void flush() {}
        @Override public void close() {}
    }
}
