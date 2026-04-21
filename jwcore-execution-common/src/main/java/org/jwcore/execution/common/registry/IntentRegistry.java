package org.jwcore.execution.common.registry;

import org.jwcore.core.time.ITimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.execution.common.emit.EventEmitter;
import org.jwcore.execution.common.events.OrderTimeoutEvent;
import org.jwcore.execution.common.runtime.PendingIntent;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class IntentRegistry {
    private static final String BROKER_TIMEOUT_REASON = "BROKER_TIMEOUT";

    private final ITimeProvider timeProvider;
    private final Map<UUID, PendingIntent> byIntentId = new HashMap<>();
    private final Map<CanonicalId, UUID> byCanonicalId = new HashMap<>();
    private final Map<UUID, IntentPhase> intentPhases = new HashMap<>();
    private final Map<UUID, Instant> submittedAtByIntentId = new HashMap<>();
    private final EventEmitter eventEmitter;
    private final java.util.Set<UUID> terminatedCorrelationIds = new java.util.HashSet<>();

    public IntentRegistry(final ITimeProvider timeProvider, final EventEmitter eventEmitter) {
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider cannot be null");
        this.eventEmitter = Objects.requireNonNull(eventEmitter, "eventEmitter cannot be null");
    }

    public void bind(final UUID intentId, final CanonicalId canonicalId, final String accountId, final Instant emittedAt, final long timeoutThresholdMs) {
        final PendingIntent pendingIntent = new PendingIntent(intentId, canonicalId, accountId, emittedAt, timeoutThresholdMs);
        byIntentId.put(intentId, pendingIntent);
        byCanonicalId.put(canonicalId, intentId);
        intentPhases.put(intentId, IntentPhase.PENDING_SUBMIT);
        submittedAtByIntentId.remove(intentId);
    }

    public Optional<CanonicalId> findCanonicalId(final UUID intentId) {
        return Optional.ofNullable(byIntentId.get(intentId)).map(PendingIntent::canonicalId);
    }

    public Optional<UUID> findIntentId(final CanonicalId canonicalId) {
        return Optional.ofNullable(byCanonicalId.get(canonicalId));
    }

    public boolean remove(final UUID intentId) {
        final PendingIntent pendingIntent = byIntentId.remove(intentId);
        if (pendingIntent == null) {
            return false;
        }
        byCanonicalId.remove(pendingIntent.canonicalId());
        intentPhases.remove(intentId);
        submittedAtByIntentId.remove(intentId);
        return true;
    }

    public void checkTimeouts(final long brokerTimeoutMs) {
        if (brokerTimeoutMs <= 0L) {
            throw new IllegalArgumentException("brokerTimeoutMs must be positive");
        }
        final Instant now = timeProvider.eventTime();
        final java.util.List<UUID> toTimeoutPending = new java.util.ArrayList<>();
        final java.util.List<UUID> toTimeoutBroker = new java.util.ArrayList<>();

        for (final PendingIntent pendingIntent : byIntentId.values()) {
            final UUID intentId = pendingIntent.intentId();
            final IntentPhase phase = intentPhases.getOrDefault(intentId, IntentPhase.PENDING_SUBMIT);
            if (phase == IntentPhase.PENDING_SUBMIT) {
                if (!now.isBefore(pendingIntent.emittedAt().plus(Duration.ofMillis(pendingIntent.timeoutThresholdMs())))) {
                    toTimeoutPending.add(intentId);
                }
                continue;
            }
            if (phase == IntentPhase.SUBMITTED) {
                final Instant submittedAt = submittedAtByIntentId.get(intentId);
                if (submittedAt != null && !now.isBefore(submittedAt.plus(Duration.ofMillis(brokerTimeoutMs)))) {
                    toTimeoutBroker.add(intentId);
                }
            }
        }

        for (final UUID intentId : toTimeoutPending) {
            final PendingIntent pendingIntent = byIntentId.get(intentId);
            if (pendingIntent == null) {
                continue;
            }
            final OrderTimeoutEvent event = eventEmitter.createOrderTimeoutEvent(pendingIntent);
            markTerminated(intentId);
            eventEmitter.emit(event.envelope());
        }

        for (final UUID intentId : toTimeoutBroker) {
            final PendingIntent pendingIntent = byIntentId.get(intentId);
            if (pendingIntent == null) {
                continue;
            }
            eventEmitter.emitOrderUnknown(pendingIntent, BROKER_TIMEOUT_REASON);
            markTerminated(intentId);
        }
    }

    public int size() {
        return byIntentId.size();
    }

    public Optional<IntentPhase> getPhase(final UUID intentId) {
        return Optional.ofNullable(intentPhases.get(intentId));
    }

    public int countInPhase(final IntentPhase phase) {
        Objects.requireNonNull(phase, "phase cannot be null");
        if (phase == IntentPhase.TERMINATED) {
            return terminatedCorrelationIds.size();
        }
        int count = 0;
        for (final IntentPhase current : intentPhases.values()) {
            if (current == phase) {
                count++;
            }
        }
        return count;
    }

    public void markSubmitted(final UUID intentId) {
        markSubmitted(intentId, timeProvider.eventTime());
    }

    private void markSubmitted(final UUID intentId, final Instant submittedAt) {
        if (intentId == null) {
            return;
        }
        if (!byIntentId.containsKey(intentId)) {
            return;
        }
        if (terminatedCorrelationIds.contains(intentId)) {
            return;
        }
        final IntentPhase currentPhase = intentPhases.get(intentId);
        if (currentPhase == IntentPhase.SUBMITTED || currentPhase == IntentPhase.TERMINATED) {
            return;
        }
        if (currentPhase != IntentPhase.PENDING_SUBMIT) {
            return;
        }
        intentPhases.put(intentId, IntentPhase.SUBMITTED);
        submittedAtByIntentId.put(intentId, submittedAt);
    }

    public void markTerminated(final UUID correlationId) {
        if (correlationId == null) {
            return;
        }
        intentPhases.put(correlationId, IntentPhase.TERMINATED);
        remove(correlationId);
        terminatedCorrelationIds.add(correlationId);
    }

    public boolean isTerminated(final UUID correlationId) {
        return correlationId != null && terminatedCorrelationIds.contains(correlationId);
    }

    public void absorb(final List<EventEnvelope> events) {
        for (final EventEnvelope event : events) {
            if (event == null) {
                continue;
            }
            if (event.eventType() == EventType.OrderRejectedEvent
                    || event.eventType() == EventType.OrderTimeoutEvent
                    || event.eventType() == EventType.OrderFilledEvent
                    || event.eventType() == EventType.OrderCanceledEvent
                    || event.eventType() == EventType.OrderUnknownEvent) {
                markTerminated(event.correlationId());
            }
            if (event.eventType() == EventType.OrderSubmittedEvent) {
                markSubmitted(event.correlationId(), event.timestampEvent());
            }
        }
    }
}
