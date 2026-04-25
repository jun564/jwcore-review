package org.jwcore.adapter.alerting;

import org.jwcore.domain.ExecutionState;
import org.jwcore.domain.events.AlertEvent;
import org.jwcore.domain.events.AlertSeverity;
import org.jwcore.domain.events.AlertType;
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
        final String halt = formatter.formatTelegramMessage(event(AlertSeverity.CRITICAL, ExecutionState.SAFE, ExecutionState.HALT, AlertType.STATE_TRANSITION, "powód-zażółć", when, List.of(), List.of()));
        final String safe = formatter.formatTelegramMessage(event(AlertSeverity.WARNING, ExecutionState.RUN, ExecutionState.SAFE, AlertType.STATE_TRANSITION, "przyczyna-ąę", when, List.of(), List.of()));
        final String run = formatter.formatTelegramMessage(event(AlertSeverity.INFO, ExecutionState.HALT, ExecutionState.RUN, AlertType.STATE_TRANSITION, "ok", when, List.of(), List.of()));
        final String kill = formatter.formatTelegramMessage(event(AlertSeverity.CRITICAL, ExecutionState.SAFE, ExecutionState.KILL, AlertType.STATE_TRANSITION, "awaria", when, List.of(), List.of()));

        assertTrue(halt.contains("ZAMROŻENIE"));
        assertTrue(halt.contains("Reset wykonywany manualnie po analizie"));
        assertTrue(safe.contains("BLOKADA"));
        assertTrue(run.contains("PRACA wznowiona"));
        assertTrue(kill.contains("STOP AWARYJNY"));
    }

    @Test
    void shouldFormatPermanentFailurePerAccount() {
        final String msg = formatter.formatTelegramMessage(event(
                AlertSeverity.CRITICAL,
                null,
                null,
                AlertType.PERMANENT_FAILURE,
                "IllegalStateException",
                Instant.parse("2026-04-24T10:15:00Z"),
                List.of("order-abcdefgh", "order-12345678"),
                List.of("ACC-1")
        ));

        assertTrue(msg.contains("PERMANENT FAILURE"));
        assertTrue(msg.contains("Konto: konto-1"));
        assertTrue(msg.contains("abcdefgh"));
    }

    @Test
    void shouldFormatPermanentFailureMultiAccountAndLimit() {
        final List<String> affected = java.util.stream.IntStream.range(0, 25).mapToObj(i -> "ACC-" + i).toList();
        final String msg = formatter.formatTelegramMessage(event(
                AlertSeverity.CRITICAL,
                null,
                null,
                AlertType.PERMANENT_FAILURE,
                "IllegalArgumentException",
                Instant.parse("2026-04-24T10:15:00Z"),
                List.of(),
                affected
        ));

        assertTrue(msg.contains("WSZYSTKIE KONTA MODUŁU"));
        assertTrue(msg.contains("+ 5 kolejnych kont"));
    }

    @Test
    void shouldFormatManualReset() {
        final String msg = formatter.formatTelegramMessage(event(AlertSeverity.INFO, ExecutionState.HALT, null, AlertType.MANUAL_RESET, "manual", Instant.parse("2026-04-24T10:15:00Z"), List.of(), List.of()));
        assertTrue(msg.contains("reset wykonany"));
        assertTrue(msg.contains("ZAMROŻENIE"));
    }

    private static AlertEvent event(final AlertSeverity severity,
                                    final ExecutionState from,
                                    final ExecutionState to,
                                    final AlertType type,
                                    final String reason,
                                    final Instant when,
                                    final List<String> brokerOrderIds,
                                    final List<String> affectedAccounts) {
        return new AlertEvent(UUID.randomUUID(), "konto-1", severity, from, to, type, reason, brokerOrderIds, when, affectedAccounts);
    }
}
