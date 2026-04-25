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
        Instant occurredAt,
        List<String> affectedAccounts) {

    private static final int PAYLOAD_VERSION = 2;
    private static final int MAX_AFFECTED_ACCOUNTS = 20;

    public AlertEvent {
        Objects.requireNonNull(alertId, "alertId cannot be null");
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
        Objects.requireNonNull(alertType, "alertType cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(brokerOrderIds, "brokerOrderIds cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(affectedAccounts, "affectedAccounts cannot be null");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
        if (brokerOrderIds.size() > 3) {
            throw new IllegalArgumentException("brokerOrderIds cannot exceed 3 elements");
        }
        if (affectedAccounts.size() > MAX_AFFECTED_ACCOUNTS) {
            throw new IllegalArgumentException("affectedAccounts cannot exceed 20 elements");
        }
        if (alertType == AlertType.STATE_TRANSITION && transitionTo == null) {
            throw new IllegalArgumentException("transitionTo cannot be null for STATE_TRANSITION");
        }
        brokerOrderIds = List.copyOf(brokerOrderIds);
        affectedAccounts = List.copyOf(affectedAccounts);
    }

    public AlertEvent(final UUID alertId,
                      final String accountId,
                      final AlertSeverity severity,
                      final ExecutionState transitionFrom,
                      final ExecutionState transitionTo,
                      final AlertType alertType,
                      final String reason,
                      final List<String> brokerOrderIds,
                      final Instant occurredAt) {
        this(alertId, accountId, severity, transitionFrom, transitionTo, alertType, reason, brokerOrderIds, occurredAt, List.of());
    }

    public byte[] toPayload() {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(baos)) {
                out.writeByte(PAYLOAD_VERSION);
                out.writeLong(alertId.getMostSignificantBits());
                out.writeLong(alertId.getLeastSignificantBits());
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
                for (final String brokerOrderId : brokerOrderIds) {
                    out.writeUTF(brokerOrderId);
                }
                out.writeLong(occurredAt.toEpochMilli());
                out.writeInt(affectedAccounts.size());
                for (final String affectedAccount : affectedAccounts) {
                    out.writeUTF(affectedAccount);
                }
            }
            return baos.toByteArray();
        } catch (final IOException exception) {
            throw new UncheckedIOException("Failed to serialize AlertEvent", exception);
        }
    }

    public static AlertEvent fromPayload(final byte[] payload) throws IOException {
        Objects.requireNonNull(payload, "payload cannot be null");
        if (payload.length > 0 && Byte.toUnsignedInt(payload[0]) == PAYLOAD_VERSION) {
            return fromPayloadV2(payload);
        }
        return fromPayloadV1(payload);
    }

    private static AlertEvent fromPayloadV2(final byte[] payload) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            final int version = Byte.toUnsignedInt(in.readByte());
            if (version != PAYLOAD_VERSION) {
                throw new IOException("Unsupported AlertEvent payload version: " + version);
            }
            final UUID alertId = new UUID(in.readLong(), in.readLong());
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

            final Instant occurredAt = Instant.ofEpochMilli(in.readLong());
            final int affectedCount = in.readInt();
            final List<String> affectedAccounts = new ArrayList<>(affectedCount);
            for (int i = 0; i < affectedCount; i++) {
                affectedAccounts.add(in.readUTF());
            }
            return new AlertEvent(alertId, accountId, severity, transitionFrom, transitionTo, alertType, reason,
                    brokerOrderIds, occurredAt, affectedAccounts);
        }
    }

    private static AlertEvent fromPayloadV1(final byte[] payload) throws IOException {
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
            return new AlertEvent(alertId, accountId, severity, transitionFrom, transitionTo, alertType, reason,
                    brokerOrderIds, occurredAt, List.of());
        }
    }
}
