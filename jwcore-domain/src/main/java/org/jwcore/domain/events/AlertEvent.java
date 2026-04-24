package org.jwcore.domain.events;

import org.jwcore.domain.ExecutionState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AlertEvent(
        UUID alertId,
        String accountId,
        AlertSeverity severity,
        ExecutionState transitionFrom,
        ExecutionState transitionTo,
        AlertType alertType,
        String reason,
        List<String> brokerOrderIds,
        Instant occurredAt) {

    public AlertEvent {
        Objects.requireNonNull(alertId, "alertId cannot be null");
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
        Objects.requireNonNull(alertType, "alertType cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(brokerOrderIds, "brokerOrderIds cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
        if (brokerOrderIds.size() > 3) {
            throw new IllegalArgumentException("brokerOrderIds cannot exceed 3 elements");
        }
        if (alertType == AlertType.STATE_TRANSITION && transitionTo == null) {
            throw new IllegalArgumentException("transitionTo cannot be null for STATE_TRANSITION");
        }
        brokerOrderIds = List.copyOf(brokerOrderIds);
    }

    public byte[] toPayload() {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(baos)) {
                out.writeUTF(alertId.toString());
                out.writeUTF(accountId);
                out.writeUTF(severity.name());

                out.writeBoolean(transitionFrom != null);
                if (transitionFrom != null) {
                    out.writeUTF(transitionFrom.name());
                }
                out.writeBoolean(transitionTo != null);
                if (transitionTo != null) {
                    out.writeUTF(transitionTo.name());
                }

                out.writeUTF(alertType.name());
                out.writeUTF(reason);
                out.writeInt(brokerOrderIds.size());
                for (String brokerOrderId : brokerOrderIds) {
                    out.writeUTF(brokerOrderId);
                }
                out.writeUTF(occurredAt.toString());
            }
            return baos.toByteArray();
        } catch (final IOException exception) {
            throw new UncheckedIOException("Failed to serialize AlertEvent", exception);
        }
    }

    public static AlertEvent fromPayload(final byte[] payload) throws IOException {
        Objects.requireNonNull(payload, "payload cannot be null");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            final UUID alertId = UUID.fromString(in.readUTF());
            final String accountId = in.readUTF();
            final AlertSeverity severity = AlertSeverity.valueOf(in.readUTF());

            final boolean hasTransitionFrom = in.readBoolean();
            final ExecutionState transitionFrom = hasTransitionFrom ? ExecutionState.valueOf(in.readUTF()) : null;

            final boolean hasTransitionTo = in.readBoolean();
            final ExecutionState transitionTo = hasTransitionTo ? ExecutionState.valueOf(in.readUTF()) : null;

            final AlertType alertType = AlertType.valueOf(in.readUTF());
            final String reason = in.readUTF();
            final int brokerOrderIdCount = in.readInt();
            final List<String> brokerOrderIds = new ArrayList<>(brokerOrderIdCount);
            for (int i = 0; i < brokerOrderIdCount; i++) {
                brokerOrderIds.add(in.readUTF());
            }
            final Instant occurredAt = Instant.parse(in.readUTF());
            return new AlertEvent(alertId, accountId, severity, transitionFrom, transitionTo, alertType, reason, brokerOrderIds, occurredAt);
        }
    }
}
