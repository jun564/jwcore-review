package org.jwcore.adapter.alerting;

import org.jwcore.domain.ExecutionState;
import org.jwcore.domain.events.AlertEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class AlertFormatter {
    private static final int MAX_TELEGRAM_LENGTH = 3500;

    public String formatTelegramMessage(final AlertEvent alert) {
        final String emoji = switch (alert.severity()) {
            case INFO -> "🟢";
            case WARNING -> "🟡";
            case CRITICAL -> "🔴";
        };

        final String time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Warsaw")).format(alert.occurredAt());
        final String date = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Warsaw")).format(alert.occurredAt());

        final String message = switch (alert.alertType()) {
            case STATE_TRANSITION -> formatStateTransition(emoji, alert, time, date);
            case MANUAL_RESET -> formatManualReset(emoji, alert, time, date);
            case PERMANENT_FAILURE -> formatPermanentFailure(emoji, alert, time, date);
        };

        if (message.length() <= MAX_TELEGRAM_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_TELEGRAM_LENGTH);
    }

    private String formatStateTransition(final String emoji, final AlertEvent alert, final String time, final String date) {
        if (alert.transitionTo() == null) {
            throw new IllegalStateException("transitionTo is required");
        }
        return switch (alert.transitionTo()) {
            case HALT -> "%s JWCore — ZAMROŻENIE\nKonto: %s (Dukascopy)\nPowód techniczny: %s\nSystem zatrzymał nowe zlecenia. Reset wykonywany manualnie po analizie.\nGodzina: %s (%s)".formatted(emoji, alert.accountId(), alert.reason(), time, date);
            case SAFE -> "%s JWCore — BLOKADA\nKonto: %s (Dukascopy)\nPowód techniczny: %s\nIstniejące pozycje działają, system nie otwiera nowych zleceń.\nGodzina: %s (%s)".formatted(emoji, alert.accountId(), alert.reason(), time, date);
            case RUN -> "%s JWCore — PRACA wznowiona\nKonto: %s (Dukascopy)\nSystem wrócił do normalnej pracy.\nGodzina: %s (%s)".formatted(emoji, alert.accountId(), time, date);
            case KILL -> "%s JWCore — STOP AWARYJNY\nKonto: %s (Dukascopy)\nPowód techniczny: %s\nSystem NIE handluje do czasu wyjaśnienia.\nGodzina: %s (%s)".formatted(emoji, alert.accountId(), alert.reason(), time, date);
        };
    }

    private String formatManualReset(final String emoji, final AlertEvent alert, final String time, final String date) {
        return "%s JWCore — reset wykonany\nKonto: %s (Dukascopy)\nPoprzedni stan: %s\nGodzina: %s (%s)"
                .formatted(emoji, alert.accountId(), toPolishState(alert.transitionFrom()), time, date);
    }

    private String formatPermanentFailure(final String emoji, final AlertEvent alert, final String time, final String date) {
        final StringBuilder builder = new StringBuilder();
        builder.append(emoji).append(" JWCore — PERMANENT FAILURE\n\n");

        final List<String> affected = alert.affectedAccounts();
        if (affected.size() > 1) {
            builder.append("Zakres: WSZYSTKIE KONTA MODUŁU\n");
        } else {
            builder.append("Konto: ").append(alert.accountId()).append(" (Dukascopy)\n");
        }

        builder.append("Moduł: ").append(alert.transitionFrom() == null ? "risk-coordinator" : alert.transitionFrom().name().toLowerCase()).append("\n");
        builder.append("Powód techniczny: event-processing-failed\n");
        builder.append("Typ eventu: ").append(alert.transitionTo() == null ? "unknown" : alert.transitionTo().name()).append("\n");
        builder.append("Błąd: ").append(alert.reason()).append("\n\n");

        if (affected.size() > 1) {
            builder.append(formatAffectedAccounts(affected));
            builder.append("\n\n");
        }

        builder.append("Ostatnie zlecenia: ").append(formatBrokerOrders(alert.brokerOrderIds())).append("\n\n");
        builder.append("Stan: SAFE\n");
        builder.append("Istniejące pozycje działają zgodnie z własnymi SL/TP.\n");
        builder.append("System nie otwiera nowych zleceń.\n");
        builder.append("Reset wykonywany manualnie po analizie.\n\n");
        builder.append("Godzina: ").append(time).append(" (").append(date).append(")");
        return builder.toString();
    }

    private String formatBrokerOrders(final List<String> brokerOrderIds) {
        if (brokerOrderIds == null || brokerOrderIds.isEmpty()) {
            return "brak";
        }
        final List<String> shortened = new ArrayList<>();
        for (final String id : brokerOrderIds) {
            shortened.add(shortenOrderId(id));
        }
        return String.join(", ", shortened);
    }

    private String formatAffectedAccounts(final List<String> affectedAccounts) {
        final StringBuilder builder = new StringBuilder("Wstrzymano konta:\n");
        final int max = Math.min(affectedAccounts.size(), 20);
        for (int i = 0; i < max; i++) {
            builder.append("- ").append(affectedAccounts.get(i)).append(" (Dukascopy)\n");
        }
        if (affectedAccounts.size() > 20) {
            builder.append("+ ").append(affectedAccounts.size() - 20).append(" kolejnych kont");
        }
        return builder.toString().trim();
    }

    private String shortenOrderId(final String fullId) {
        if (fullId == null || fullId.isBlank()) {
            return "brak";
        }
        if (fullId.length() <= 8) {
            return fullId;
        }
        return fullId.substring(fullId.length() - 8);
    }

    private String toPolishState(final ExecutionState state) {
        if (state == null) {
            return "brak";
        }
        return switch (state) {
            case RUN -> "PRACA";
            case SAFE -> "BLOKADA";
            case HALT -> "ZAMROŻENIE";
            case KILL -> "STOP AWARYJNY";
        };
    }
}
