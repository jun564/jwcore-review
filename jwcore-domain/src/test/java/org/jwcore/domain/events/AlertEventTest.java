package org.jwcore.domain.events;

import org.jwcore.domain.ExecutionState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlertEventTest {

    @Test
    void shouldRoundtripPayloadWithAllFields() throws IOException {
        final AlertEvent event = new AlertEvent(
                UUID.randomUUID(),
                "konto-ążę",
                AlertSeverity.CRITICAL,
                ExecutionState.SAFE,
                ExecutionState.HALT,
                AlertType.STATE_TRANSITION,
                "powód-żółw",
                List.of("A", "B"),
                Instant.parse("2026-04-24T12:00:00Z")
        );

        final AlertEvent parsed = AlertEvent.fromPayload(event.toPayload());

        assertEquals(event, parsed);
    }

    @Test
    void shouldRoundtripNullableTransitions() throws IOException {
        final AlertEvent event = new AlertEvent(
                UUID.randomUUID(),
                "konto",
                AlertSeverity.INFO,
                null,
                null,
                AlertType.MANUAL_RESET,
                "manual-risk-reset",
                List.of(),
                Instant.parse("2026-04-24T12:00:00Z")
        );

        final AlertEvent parsed = AlertEvent.fromPayload(event.toPayload());

        assertEquals(event.transitionFrom(), parsed.transitionFrom());
        assertEquals(event.transitionTo(), parsed.transitionTo());
    }

    @Test
    void shouldValidateRequiredFields() {
        assertThrows(NullPointerException.class, () -> new AlertEvent(null, "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of(), Instant.now()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), null, AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of(), Instant.now()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), "a", null, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of(), Instant.now()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, null, "r", List.of(), Instant.now()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, null, List.of(), Instant.now()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", null, Instant.now()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of(), null));
    }

    @Test
    void shouldRejectInvalidRules() {
        assertThrows(IllegalArgumentException.class, () -> new AlertEvent(UUID.randomUUID(), " ", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of(), Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, " ", List.of(), Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of("1", "2", "3", "4"), Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, null, AlertType.STATE_TRANSITION, "r", List.of(), Instant.now()));
    }
}
