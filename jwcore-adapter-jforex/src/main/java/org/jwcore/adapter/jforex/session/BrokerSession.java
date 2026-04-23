package org.jwcore.adapter.jforex.session;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.events.DriftClassification;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public final class BrokerSession {
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    private final ScheduledExecutorService scheduler;
    private final BooleanSupplier reconnectAttempt;
    private final Clock clock;
    private final AtomicReference<BrokerSessionState> state = new AtomicReference<>(BrokerSessionState.CONNECTED);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final ConcurrentHashMap<CanonicalId, DriftClassification> activeDrifts = new ConcurrentHashMap<>();
    private volatile Optional<Instant> failedAt = Optional.empty();

    public BrokerSession(final ScheduledExecutorService scheduler,
                         final BooleanSupplier reconnectAttempt,
                         final Clock clock) {
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler cannot be null");
        this.reconnectAttempt = java.util.Objects.requireNonNull(reconnectAttempt, "reconnectAttempt cannot be null");
        this.clock = java.util.Objects.requireNonNull(clock, "clock cannot be null");
    }

    public synchronized void transitionTo(final BrokerSessionState target) {
        final BrokerSessionState current = state.get();
        if (!isAllowedTransition(current, target)) {
            throw new IllegalStateException("Illegal broker session transition: " + current + " -> " + target);
        }
        state.set(target);
        if (target == BrokerSessionState.FAILED) {
            failedAt = Optional.of(Instant.now(clock));
        }
    }

    public synchronized void onHeartbeatTimeout() {
        transitionTo(BrokerSessionState.DISCONNECTED);
    }

    public synchronized void onBrokerException(final Exception exception) {
        java.util.Objects.requireNonNull(exception, "exception cannot be null");
        transitionTo(BrokerSessionState.DISCONNECTED);
    }

    public synchronized void startReconnect() {
        transitionTo(BrokerSessionState.RECONNECTING);
        scheduleNextAttempt();
    }

    public synchronized void onReconnectSuccess() {
        transitionTo(BrokerSessionState.RECONCILING);
        reconnectAttempts.set(0);
    }

    public synchronized void onReconcileComplete(final DriftClassification driftClassification) {
        java.util.Objects.requireNonNull(driftClassification, "driftClassification cannot be null");
        if (driftClassification == DriftClassification.MAJOR || driftClassification == DriftClassification.FATAL) {
            transitionTo(BrokerSessionState.FAILED);
            return;
        }
        transitionTo(BrokerSessionState.CONNECTED);
    }

    public boolean canAcceptIntent(final CanonicalId canonicalId) {
        if (state.get() != BrokerSessionState.CONNECTED) {
            return false;
        }
        final DriftClassification drift = activeDrifts.get(canonicalId);
        return drift == null || drift.isAcceptable();
    }

    public void updateActiveDrift(final CanonicalId canonicalId, final DriftClassification classification) {
        java.util.Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        java.util.Objects.requireNonNull(classification, "classification cannot be null");
        if (classification.isAcceptable()) {
            activeDrifts.remove(canonicalId);
            return;
        }
        activeDrifts.put(canonicalId, classification);
    }

    public BrokerSessionState state() {
        return state.get();
    }

    public int reconnectAttempts() {
        return reconnectAttempts.get();
    }

    public Optional<Instant> failedAt() {
        return failedAt;
    }

    public Map<CanonicalId, DriftClassification> activeDrifts() {
        return Map.copyOf(activeDrifts);
    }

    private void scheduleNextAttempt() {
        final int currentAttempt = reconnectAttempts.incrementAndGet();
        if (currentAttempt > MAX_RECONNECT_ATTEMPTS) {
            transitionTo(BrokerSessionState.FAILED);
            return;
        }
        final long delaySeconds = 1L << (currentAttempt - 1);
        scheduler.schedule(() -> {
            final boolean success = reconnectAttempt.getAsBoolean();
            synchronized (BrokerSession.this) {
                if (state.get() != BrokerSessionState.RECONNECTING) {
                    return;
                }
                if (success) {
                    onReconnectSuccess();
                    return;
                }
                if (reconnectAttempts.get() >= MAX_RECONNECT_ATTEMPTS) {
                    transitionTo(BrokerSessionState.FAILED);
                    return;
                }
                scheduleNextAttempt();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private static boolean isAllowedTransition(final BrokerSessionState current, final BrokerSessionState target) {
        return switch (current) {
            case CONNECTED -> target == BrokerSessionState.DISCONNECTED;
            case DISCONNECTED -> target == BrokerSessionState.RECONNECTING;
            case RECONNECTING -> target == BrokerSessionState.RECONNECTING
                    || target == BrokerSessionState.RECONCILING
                    || target == BrokerSessionState.FAILED;
            case RECONCILING -> target == BrokerSessionState.CONNECTED
                    || target == BrokerSessionState.FAILED;
            case FAILED -> false;
        };
    }
}
