package org.jwcore.execution.common.emit;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.time.ITimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.jwcore.execution.common.events.*;
import org.jwcore.execution.common.runtime.PendingIntent;
import org.jwcore.execution.common.state.ExecutionState;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class EventEmitter {
    private static final byte PAYLOAD_VERSION = 2;

    private final IEventJournal eventJournal;
    private final ITimeProvider timeProvider;
    private final String sourceProcessId;

    public EventEmitter(final IEventJournal eventJournal, final ITimeProvider timeProvider) {
        this(eventJournal, timeProvider, "unknown");
    }

    public EventEmitter(final IEventJournal eventJournal, final ITimeProvider timeProvider, final String sourceProcessId) {
        this.eventJournal = Objects.requireNonNull(eventJournal, "eventJournal cannot be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider cannot be null");
        this.sourceProcessId = Objects.requireNonNull(sourceProcessId, "sourceProcessId cannot be null");
        if (sourceProcessId.isBlank()) {
            throw new IllegalArgumentException("sourceProcessId cannot be blank");
        }
    }

    public void emit(final EventEnvelope envelope) {
        eventJournal.append(envelope);
    }

    public EventEnvelope createEnvelope(final EventType eventType, final String brokerOrderId, final String localIntentId,
                                        final CanonicalId canonicalId, final byte[] payload) {
        final byte[] safePayload = payload == null ? new byte[0] : payload.clone();
        final UUID eventId = UUID.randomUUID();
        return new EventEnvelope(
                eventId,
                eventType,
                brokerOrderId,
                localIntentId,
                canonicalId,
                IdempotencyKeys.generate(brokerOrderId, eventType, safePayload),
                timeProvider.monotonicTime(),
                timeProvider.eventTime(),
                PAYLOAD_VERSION,
                safePayload,
                sourceProcessId,
                eventId
        );
    }

    public EventEnvelope createEnvelope(final EventType eventType, final String brokerOrderId, final String localIntentId,
                                        final CanonicalId canonicalId, final byte[] payload, final UUID correlationId) {
        final byte[] safePayload = payload == null ? new byte[0] : payload.clone();
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                brokerOrderId,
                localIntentId,
                canonicalId,
                IdempotencyKeys.generate(brokerOrderId, eventType, safePayload),
                timeProvider.monotonicTime(),
                timeProvider.eventTime(),
                PAYLOAD_VERSION,
                safePayload,
                sourceProcessId,
                Objects.requireNonNull(correlationId, "correlationId cannot be null")
        );
    }

    public RiskDecisionEvent createRiskDecisionEvent(final String accountId, final ExecutionState desiredState, final String reason) {
        final byte[] payload = String.join("|", accountId, desiredState.name(), reason).getBytes(StandardCharsets.UTF_8);
        final EventEnvelope envelope = createEnvelope(EventType.RiskDecisionEvent, null, null, null, payload);
        return new RiskDecisionEvent(accountId, desiredState, reason, envelope);
    }

    public RiskDecisionEvent parseRiskDecisionEvent(final EventEnvelope envelope) {
        final String[] parts = new String(envelope.payload(), StandardCharsets.UTF_8).split("\\|", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid RiskDecisionEvent payload");
        }
        return new RiskDecisionEvent(parts[0], ExecutionState.valueOf(parts[1]), parts[2], envelope);
    }

    public MarginUpdateEvent createMarginUpdateEvent(final String accountId, final BigDecimal marginLevel,
                                                     final BigDecimal freeMargin, final BigDecimal equity) {
        final byte[] payload = String.join("|", accountId, marginLevel.toPlainString(), freeMargin.toPlainString(), equity.toPlainString())
                .getBytes(StandardCharsets.UTF_8);
        final EventEnvelope envelope = createEnvelope(EventType.MarginUpdateEvent, null, null, null, payload);
        return new MarginUpdateEvent(accountId, marginLevel, freeMargin, equity, envelope);
    }

    public OrderTimeoutEvent createOrderTimeoutEvent(final PendingIntent pendingIntent) {
        final String text = String.join("|", pendingIntent.intentId().toString(), pendingIntent.canonicalId().format(), pendingIntent.accountId(),
                Long.toString(pendingIntent.timeoutThresholdMs()), pendingIntent.emittedAt().toString(), timeProvider.eventTime().toString());
        final EventEnvelope envelope = createEnvelope(EventType.OrderTimeoutEvent, null, pendingIntent.intentId().toString(),
                pendingIntent.canonicalId(), text.getBytes(StandardCharsets.UTF_8), pendingIntent.intentId());
        return new OrderTimeoutEvent(pendingIntent.intentId(), pendingIntent.canonicalId(), pendingIntent.accountId(),
                pendingIntent.timeoutThresholdMs(), pendingIntent.emittedAt(), timeProvider.eventTime(), envelope);
    }

    public StateRebuiltEvent createStateRebuiltEvent(final String accountId, final int snapshotVersion, final UUID rebuiltUntilEventId,
                                                     final Instant rebuiltUntilTimestamp, final RebuildType type, final int eventsReplayed,
                                                     final List<Discrepancy> discrepancies) {
        final byte[] payload = String.join("|", accountId, Integer.toString(snapshotVersion), rebuiltUntilEventId.toString(),
                rebuiltUntilTimestamp.toString(), type.name(), Integer.toString(eventsReplayed), Integer.toString(discrepancies.size()))
                .getBytes(StandardCharsets.UTF_8);
        final EventEnvelope envelope = createEnvelope(EventType.StateRebuiltEvent, null, null, null, payload);
        return new StateRebuiltEvent(accountId, snapshotVersion, rebuiltUntilEventId, rebuiltUntilTimestamp, type, eventsReplayed, discrepancies, envelope);
    }
}
