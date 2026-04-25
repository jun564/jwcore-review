package org.jwcore.domain.events;

import org.jwcore.domain.ExecutionState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlertEventTest {

    @Test
    void shouldRoundtripV2PayloadWithAffectedAccounts() throws IOException {
        final AlertEvent event = new AlertEvent(
                UUID.randomUUID(),
                "konto-ążę",
                AlertSeverity.CRITICAL,
                ExecutionState.SAFE,
                ExecutionState.HALT,
                AlertType.PERMANENT_FAILURE,
                "powód-żółw",
                List.of("ORD-123456789", "ORD-123456780"),
                Instant.parse("2026-04-24T12:00:00Z"),
                List.of("ACC-1", "ACC-2")
        );

        final AlertEvent parsed = AlertEvent.fromPayload(event.toPayload());

        assertEquals(event, parsed);
    }

    @Test
    void shouldReadLegacyV1PayloadWithoutAffectedAccounts() throws IOException {
        final UUID id = UUID.randomUUID();
        final byte[] legacyPayload;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(baos)) {
            out.writeUTF(id.toString());
            out.writeUTF("konto");
            out.writeUTF(AlertSeverity.INFO.name());
            out.writeBoolean(false);
            out.writeBoolean(false);
            out.writeUTF(AlertType.MANUAL_RESET.name());
            out.writeUTF("manual-risk-reset");
            out.writeInt(0);
            out.writeUTF("2026-04-24T12:00:00Z");
            legacyPayload = baos.toByteArray();
        }

        final AlertEvent parsed = AlertEvent.fromPayload(legacyPayload);

        assertEquals(List.of(), parsed.affectedAccounts());
        assertEquals("konto", parsed.accountId());
    }

    @Test
    void shouldValidateRequiredFieldsAndAffectedAccountsLimit() {
        assertThrows(NullPointerException.class, () -> new AlertEvent(null, "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of(), Instant.now(), List.of()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), null, AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of(), Instant.now(), List.of()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), "a", null, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of(), Instant.now(), List.of()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, null, "r", List.of(), Instant.now(), List.of()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, null, List.of(), Instant.now(), List.of()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", null, Instant.now(), List.of()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of(), null, List.of()));
        assertThrows(NullPointerException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of(), Instant.now(), null));

        assertThrows(IllegalArgumentException.class, () -> new AlertEvent(UUID.randomUUID(), " ", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of(), Instant.now(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, " ", List.of(), Instant.now(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, ExecutionState.RUN, AlertType.STATE_TRANSITION, "r", List.of("1", "2", "3", "4"), Instant.now(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, null, AlertType.STATE_TRANSITION, "r", List.of(), Instant.now(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AlertEvent(UUID.randomUUID(), "a", AlertSeverity.INFO, null, null, AlertType.MANUAL_RESET, "r", List.of(), Instant.now(), java.util.stream.Stream.generate(() -> "x").limit(101).toList()));
    }
}
