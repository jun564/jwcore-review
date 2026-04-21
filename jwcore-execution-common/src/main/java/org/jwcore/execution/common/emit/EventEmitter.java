package org.jwcore.execution.common.emit;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.time.ITimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.jwcore.domain.RejectReason;
import org.jwcore.domain.events.EventProcessingFailedEvent;
import org.jwcore.domain.events.OrderRejectedEvent;
import org.jwcore.domain.events.OrderSubmittedEvent;
import org.jwcore.domain.events.OrderUnknownEvent;
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
    private static final int ERROR_MESSAGE_MAX_LENGTH = 512;

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
        final String idempotencyKey = "risk-decision:" + accountId + ":" + desiredState.name() + ":" + reason;
        final EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                EventType.RiskDecisionEvent,
                null,
                null,
                null,
                idempotencyKey,
                timeProvider.monotonicTime(),
                timeProvider.eventTime(),
                PAYLOAD_VERSION,
                payload,
                sourceProcessId,
                null
        );
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

    public OrderRejectedEvent emitOrderRejected(final PendingIntent intent, final RejectReason reason) {
        Objects.requireNonNull(intent, "intent cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        final Instant now = timeProvider.eventTime();
        final byte[] payload = String.join("|", intent.accountId(), intent.intentId().toString(), reason.name(), now.toString())
                .getBytes(StandardCharsets.UTF_8);
        final EventEnvelope envelope = createEnvelope(
                EventType.OrderRejectedEvent,
                null,
                intent.intentId().toString(),
                intent.canonicalId(),
                payload,
                intent.intentId()
        );
        emit(envelope);
        return new OrderRejectedEvent(intent.accountId(), intent.intentId().toString(), reason, now, envelope);
    }


    public OrderSubmittedEvent emitOrderSubmitted(final PendingIntent intent, final String brokerOrderId, final double size) {
        Objects.requireNonNull(intent, "intent cannot be null");
        Objects.requireNonNull(brokerOrderId, "brokerOrderId cannot be null");
        if (brokerOrderId.isBlank()) {
            throw new IllegalArgumentException("brokerOrderId cannot be blank");
        }
        if (!Double.isFinite(size) || size <= 0.0d) {
            throw new IllegalArgumentException("size must be positive finite");
        }
        final Instant now = timeProvider.eventTime();
        final OrderSubmittedEvent event = new OrderSubmittedEvent(
                intent.accountId(),
                intent.intentId(),
                brokerOrderId,
                intent.canonicalId(),
                size,
                now,
                null
        );
        final EventEnvelope envelope = createEnvelope(
                EventType.OrderSubmittedEvent,
                brokerOrderId,
                intent.intentId().toString(),
                intent.canonicalId(),
                event.toPayload(),
                intent.intentId()
        );
        final OrderSubmittedEvent emittedEvent = new OrderSubmittedEvent(
                intent.accountId(),
                intent.intentId(),
                brokerOrderId,
                intent.canonicalId(),
                size,
                now,
                envelope
        );
        emit(envelope);
        return emittedEvent;
    }

    public OrderUnknownEvent emitOrderUnknown(final PendingIntent intent, final String reason) {
        Objects.requireNonNull(intent, "intent cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
        final Instant now = timeProvider.eventTime();
        final byte[] payload = String.join("|", intent.accountId(), intent.intentId().toString(), reason, now.toString())
                .getBytes(StandardCharsets.UTF_8);
        final EventEnvelope envelope = createEnvelope(
                EventType.OrderUnknownEvent,
                null,
                intent.intentId().toString(),
                intent.canonicalId(),
                payload,
                intent.intentId()
        );
        emit(envelope);
        return new OrderUnknownEvent(intent.accountId(), intent.intentId().toString(), reason, now, envelope);
    }

    public EventProcessingFailedEvent emitEventProcessingFailed(final UUID failedEventId, final Throwable exception) {
        Objects.requireNonNull(failedEventId, "failedEventId cannot be null");
        Objects.requireNonNull(exception, "exception cannot be null");
        final Instant now = timeProvider.eventTime();
        final String errorType = exception.getClass().getName();
        final String errorMessage = truncateErrorMessage(Objects.toString(exception.getMessage(), ""));
        final byte[] payload = String.join("|",
                        failedEventId.toString(),
                        errorType,
                        errorMessage,
                        now.toString())
                .getBytes(StandardCharsets.UTF_8);
        final EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                EventType.EventProcessingFailedEvent,
                null,
                failedEventId.toString(),
                null,
                IdempotencyKeys.generate(null, EventType.EventProcessingFailedEvent, payload),
                timeProvider.monotonicTime(),
                now,
                PAYLOAD_VERSION,
                payload,
                sourceProcessId,
                null
        );
        emit(envelope);
        return new EventProcessingFailedEvent(failedEventId, errorType, errorMessage, now, envelope);
    }

    private static String truncateErrorMessage(final String message) {
        if (message.length() <= ERROR_MESSAGE_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
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
