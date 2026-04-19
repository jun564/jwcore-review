package org.jwcore.execution.common.registry;

import org.jwcore.core.time.ITimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.execution.common.emit.EventEmitter;
import org.jwcore.execution.common.events.OrderTimeoutEvent;
import org.jwcore.execution.common.runtime.OrderTimeoutTracker;
import org.jwcore.execution.common.runtime.PendingIntent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class IntentRegistry {
    private final Map<UUID, PendingIntent> byIntentId = new HashMap<>();
    private final Map<CanonicalId, UUID> byCanonicalId = new HashMap<>();
    private final OrderTimeoutTracker timeoutTracker;
    private final EventEmitter eventEmitter;

    public IntentRegistry(final ITimeProvider timeProvider, final EventEmitter eventEmitter) {
        this.timeoutTracker = new OrderTimeoutTracker(Objects.requireNonNull(timeProvider, "timeProvider cannot be null"));
        this.eventEmitter = Objects.requireNonNull(eventEmitter, "eventEmitter cannot be null");
    }

    public void bind(final UUID intentId, final CanonicalId canonicalId, final String accountId, final Instant emittedAt, final long timeoutThresholdMs) {
        final PendingIntent pendingIntent = new PendingIntent(intentId, canonicalId, accountId, emittedAt, timeoutThresholdMs);
        byIntentId.put(intentId, pendingIntent);
        byCanonicalId.put(canonicalId, intentId);
        timeoutTracker.register(pendingIntent);
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
        timeoutTracker.remove(intentId);
        return true;
    }

    public void checkTimeouts() {
        timeoutTracker.checkTimeouts(eventEmitter::createOrderTimeoutEvent, this::handleTimeout);
    }

    public int size() {
        return byIntentId.size();
    }

    private void handleTimeout(final OrderTimeoutEvent orderTimeoutEvent) {
        remove(orderTimeoutEvent.intentId());
        eventEmitter.emit(orderTimeoutEvent.envelope());
    }
}
