package org.jwcore.adapter.alerting;

import org.jwcore.domain.events.AlertEvent;
import org.jwcore.domain.ExecutionState;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class AlertFormatter {
    public String formatTelegramMessage(final AlertEvent alert) {
        final String emoji = switch (alert.severity()) {
            case INFO -> "🟢";
            case WARNING -> "🟡";
            case CRITICAL -> "🔴";
        };

        final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Warsaw"));
        final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Warsaw"));
        final String time = timeFmt.format(alert.occurredAt());
        final String date = dateFmt.format(alert.occurredAt());

        return switch (alert.alertType()) {
            case STATE_TRANSITION -> formatStateTransition(emoji, alert, time, date);
            case MANUAL_RESET -> formatManualReset(emoji, alert, time, date);
        };
    }

    private String formatStateTransition(final String emoji, final AlertEvent alert, final String time, final String date) {
        if (alert.transitionTo() == null) {
            throw new IllegalStateException("transitionTo is required");
        }
        return switch (alert.transitionTo()) {
            case HALT -> "%s JWCore — ZAMROŻENIE\nKonto: %s (Dukascopy)\nPowód techniczny: %s\nSystem zatrzymał nowe zlecenia. Wymaga ręcznego resetu.\nGodzina: %s (%s)".formatted(emoji, alert.accountId(), alert.reason(), time, date);
            case SAFE -> "%s JWCore — BLOKADA\nKonto: %s (Dukascopy)\nPowód techniczny: %s\nIstniejące pozycje działają, system nie otwiera nowych zleceń.\nGodzina: %s (%s)".formatted(emoji, alert.accountId(), alert.reason(), time, date);
            case RUN -> "%s JWCore — PRACA wznowiona\nKonto: %s (Dukascopy)\nSystem wrócił do normalnej pracy.\nGodzina: %s (%s)".formatted(emoji, alert.accountId(), time, date);
            case KILL -> "%s JWCore — STOP AWARYJNY\nKonto: %s (Dukascopy)\nPowód techniczny: %s\nSystem NIE handluje do czasu wyjaśnienia.\nGodzina: %s (%s)".formatted(emoji, alert.accountId(), alert.reason(), time, date);
        };
    }

    private String formatManualReset(final String emoji, final AlertEvent alert, final String time, final String date) {
        return "%s JWCore — reset wykonany\nKonto: %s (Dukascopy)\nPoprzedni stan: %s\nGodzina: %s (%s)"
                .formatted(emoji, alert.accountId(), toPolishState(alert.transitionFrom()), time, date);
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
