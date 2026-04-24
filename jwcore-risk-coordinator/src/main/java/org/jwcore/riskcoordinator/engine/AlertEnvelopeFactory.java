package org.jwcore.riskcoordinator.engine;

import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.events.AlertEvent;

public final class AlertEnvelopeFactory {

    private final String sourceProcessId;

    public AlertEnvelopeFactory(final String sourceProcessId) {
        this.sourceProcessId = sourceProcessId;
    }

    public EventEnvelope buildEnvelope(final AlertEvent alert) {
        final byte[] payload = alert.toPayload();
        final String fromPart = alert.transitionFrom() != null ? alert.transitionFrom().name() : "null";
        final String toPart = alert.transitionTo() != null ? alert.transitionTo().name() : "null";
        final String idempotencyKey = "alert:" + alert.accountId() + ":" + fromPart + "->" + toPart + ":"
                + alert.occurredAt().toEpochMilli() + ":" + alert.alertId();

        return new EventEnvelope(
                alert.alertId(),
                EventType.AlertEvent,
                null,
                null,
                null,
                idempotencyKey,
                0L,
                alert.occurredAt(),
                (byte) 1,
                payload,
                sourceProcessId,
                null
        );
    }
}
