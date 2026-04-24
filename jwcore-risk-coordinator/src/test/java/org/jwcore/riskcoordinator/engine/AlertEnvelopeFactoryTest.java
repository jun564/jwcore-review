package org.jwcore.riskcoordinator.engine;

import org.jwcore.domain.EventType;
import org.jwcore.domain.events.AlertEvent;
import org.jwcore.domain.events.AlertSeverity;
import org.jwcore.domain.events.AlertType;
import org.jwcore.domain.ExecutionState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertEnvelopeFactoryTest {

    @Test
    void shouldBuildAlertEnvelope() throws IOException {
        final UUID alertId = UUID.randomUUID();
        final Instant occurredAt = Instant.parse("2026-04-24T12:00:00Z");
        final AlertEvent alert = new AlertEvent(alertId, "acc-1", AlertSeverity.WARNING,
                ExecutionState.RUN, ExecutionState.SAFE, AlertType.STATE_TRANSITION,
                "state-transition", List.of(), occurredAt);

        final AlertEnvelopeFactory factory = new AlertEnvelopeFactory("risk-node");
        final var envelope = factory.buildEnvelope(alert);

        assertEquals(EventType.AlertEvent, envelope.eventType());
        assertEquals("alert:acc-1:RUN->SAFE:" + occurredAt.toEpochMilli() + ":" + alertId, envelope.idempotencyKey());
        assertEquals(alert, AlertEvent.fromPayload(envelope.payload()));
        assertTrue(envelope.idempotencyKey().contains(alertId.toString()));
    }
}
