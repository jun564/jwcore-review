package org.jwcore.adapter.alerting;

import org.jwcore.domain.events.AlertEvent;
import org.jwcore.domain.events.AlertSeverity;
import org.jwcore.domain.events.AlertType;
import org.jwcore.domain.ExecutionState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertFormatterTest {
    private final AlertFormatter formatter = new AlertFormatter();

    @Test
    void shouldFormatAllStateTransitionsWithPolishCharacters() {
        final Instant when = Instant.parse("2026-04-24T10:15:00Z");
        final String halt = formatter.formatTelegramMessage(event(AlertSeverity.CRITICAL, ExecutionState.SAFE, ExecutionState.HALT, AlertType.STATE_TRANSITION, "powód-zażółć", when));
        final String safe = formatter.formatTelegramMessage(event(AlertSeverity.WARNING, ExecutionState.RUN, ExecutionState.SAFE, AlertType.STATE_TRANSITION, "przyczyna-ąę", when));
        final String run = formatter.formatTelegramMessage(event(AlertSeverity.INFO, ExecutionState.HALT, ExecutionState.RUN, AlertType.STATE_TRANSITION, "ok", when));
        final String kill = formatter.formatTelegramMessage(event(AlertSeverity.CRITICAL, ExecutionState.SAFE, ExecutionState.KILL, AlertType.STATE_TRANSITION, "awaria", when));

        assertTrue(halt.contains("ZAMROŻENIE"));
        assertTrue(halt.contains("zażółć"));
        assertTrue(safe.contains("BLOKADA"));
        assertTrue(run.contains("PRACA wznowiona"));
        assertTrue(kill.contains("STOP AWARYJNY"));
        assertTrue(halt.contains("12:15"));
        assertTrue(halt.contains("24.04.2026"));
        assertTrue(halt.contains("🔴"));
        assertTrue(safe.contains("🟡"));
        assertTrue(run.contains("🟢"));
    }

    @Test
    void shouldFormatManualReset() {
        final String msg = formatter.formatTelegramMessage(event(AlertSeverity.INFO, ExecutionState.HALT, null, AlertType.MANUAL_RESET, "manual", Instant.parse("2026-04-24T10:15:00Z")));
        assertTrue(msg.contains("reset wykonany"));
        assertTrue(msg.contains("ZAMROŻENIE"));
    }

    private static AlertEvent event(final AlertSeverity severity,
                                    final ExecutionState from,
                                    final ExecutionState to,
                                    final AlertType type,
                                    final String reason,
                                    final Instant when) {
        return new AlertEvent(UUID.randomUUID(), "konto-1", severity, from, to, type, reason, List.of(), when);
    }
}
