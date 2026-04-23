package org.jwcore.riskcoordinator.engine;

import org.jwcore.core.time.ITimeProvider;
import org.jwcore.core.time.RealTimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.events.OrderCanceledEvent;
import org.jwcore.domain.events.OrderFilledEvent;
import org.jwcore.domain.events.OrderIntentEvent;
import org.jwcore.domain.events.OrderRejectedEvent;
import org.jwcore.domain.events.OrderSubmittedEvent;
import org.jwcore.domain.events.OrderUnknownEvent;
import org.jwcore.execution.common.events.RiskDecisionEvent;
import org.jwcore.execution.common.state.ExecutionState;
import org.jwcore.riskcoordinator.command.RiskStateResetCommand;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class RiskCoordinatorEngine {
    private static final Logger LOGGER = Logger.getLogger(RiskCoordinatorEngine.class.getName());
    private static final String REASON_DEFAULT_RUN = "default-run";
    private static final String REASON_ORDER_UNKNOWN = "order-unknown-event";

    private final ExposureLedger exposureLedger;
    private final String sourceProcessId;
    private final ITimeProvider timeProvider;
    private final int haltThresholdCount;
    private final Duration haltWindowDuration;
    private final AtomicReference<Map<String, ExecutionState>> accountStates = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, ExecutionState>> lastEmittedStates = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, CanonicalId>> canonicalByAccount = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, java.math.BigDecimal>> exposureByAccount = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<CanonicalId, Deque<Instant>>> unknownTimestamps = new AtomicReference<>(Map.of());

    public RiskCoordinatorEngine(final String sourceProcessId) {
        this(new ExposureLedger(), sourceProcessId, new RealTimeProvider(), 3, Duration.ofSeconds(60));
    }

    public RiskCoordinatorEngine(final ExposureLedger exposureLedger, final String sourceProcessId) {
        this(exposureLedger, sourceProcessId, new RealTimeProvider(), 3, Duration.ofSeconds(60));
    }

    public RiskCoordinatorEngine(final ExposureLedger exposureLedger,
                                 final String sourceProcessId,
                                 final ITimeProvider timeProvider,
                                 final int haltThresholdCount,
                                 final Duration haltWindowDuration) {
        this.exposureLedger = Objects.requireNonNull(exposureLedger, "exposureLedger cannot be null");
        this.sourceProcessId = Objects.requireNonNull(sourceProcessId, "sourceProcessId cannot be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider cannot be null");
        if (sourceProcessId.isBlank()) {
            throw new IllegalArgumentException("sourceProcessId cannot be blank");
        }
        if (haltThresholdCount <= 0) {
            throw new IllegalArgumentException("haltThresholdCount must be positive");
        }
        if (haltWindowDuration.isNegative() || haltWindowDuration.isZero()) {
            throw new IllegalArgumentException("haltWindowDuration must be positive");
        }
        this.haltThresholdCount = haltThresholdCount;
        this.haltWindowDuration = haltWindowDuration;
    }

    public Set<String> apply(final EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        try {
            if (envelope.eventType() == EventType.OrderSubmittedEvent) {
                final OrderSubmittedEvent event = OrderSubmittedEvent.fromPayload(envelope.payload());
                bindCanonicalToAccount(event.accountId(), event.canonicalId());
                recomputeExposure(event.accountId());
                return Set.of(event.accountId());
            }
            if (envelope.eventType() == EventType.OrderIntentEvent) {
                final OrderIntentEvent event = OrderIntentEvent.fromPayload(envelope.payload());
                final String accountId = event.accountId();
                if (isHalted(accountId)) {
                    return Set.of(accountId);
                }
                exposureLedger.apply(envelope);
                return Set.of(accountId);
            }
            if (envelope.eventType() == EventType.OrderFilledEvent) {
                final OrderFilledEvent event = OrderFilledEvent.fromPayload(envelope.payload());
                final String accountId = resolveAccountId(event.canonicalId());
                if (isHalted(accountId)) {
                    return Set.of(accountId);
                }
                bindCanonicalToAccount(accountId, event.canonicalId());
                exposureLedger.apply(envelope);
                recomputeExposure(accountId);
                return Set.of(accountId);
            }
            if (envelope.eventType() == EventType.OrderCanceledEvent) {
                final OrderCanceledEvent event = OrderCanceledEvent.fromPayload(envelope.payload());
                final String accountId = resolveAccountId(event.canonicalId());
                if (isHalted(accountId)) {
                    return Set.of(accountId);
                }
                bindCanonicalToAccount(accountId, event.canonicalId());
                exposureLedger.apply(envelope);
                recomputeExposure(accountId);
                return Set.of(accountId);
            }
            if (envelope.eventType() == EventType.OrderRejectedEvent) {
                final OrderRejectedEvent event = OrderRejectedEvent.fromPayload(envelope.payload());
                final String accountId = envelope.canonicalId() == null ? event.accountId() : resolveAccountId(envelope.canonicalId());
                if (isHalted(accountId)) {
                    return Set.of(accountId);
                }
                exposureLedger.apply(envelope);
                recomputeExposure(accountId);
                return Set.of(accountId);
            }
            if (envelope.eventType() == EventType.OrderUnknownEvent) {
                final OrderUnknownEvent event = OrderUnknownEvent.fromPayload(envelope.payload());
                setAccountState(event.accountId(), ExecutionState.SAFE);
                final CanonicalId canonicalId = canonicalByAccount.get().get(event.accountId());
                if (canonicalId != null) {
                    trackUnknownAndMaybeEscalate(event.accountId(), canonicalId);
                }
                return Set.of(event.accountId());
            }
            return Set.of();
        } catch (final RuntimeException exception) {
            LOGGER.warning(() -> "Skipping malformed event in risk engine, eventType=" + envelope.eventType()
                    + ", eventId=" + envelope.eventId() + ", reason=" + exception.getMessage());
            return Set.of();
        }
    }

    public Optional<RiskDecisionEvent> evaluateAndBuildIfChanged(final String accountId) {
        validateAccountId(accountId);
        final ExecutionState desiredState = accountStates.get().getOrDefault(accountId, ExecutionState.RUN);
        final ExecutionState previous = lastEmittedStates.get().get(accountId);
        if (desiredState == previous) {
            return Optional.empty();
        }

        final Map<String, ExecutionState> updated = new HashMap<>(lastEmittedStates.get());
        updated.put(accountId, desiredState);
        lastEmittedStates.set(updated);

        return Optional.of(buildRiskDecision(accountId, desiredState, reasonFor(accountId, desiredState)));
    }

    public List<RiskDecisionEvent> initialPublishFromCurrentState(final List<String> monitoredAccounts) {
        Objects.requireNonNull(monitoredAccounts, "monitoredAccounts cannot be null");
        final Map<String, ExecutionState> current = accountStates.get();
        final Map<String, ExecutionState> emitted = new HashMap<>();
        final List<RiskDecisionEvent> events = new ArrayList<>();

        for (final String accountId : monitoredAccounts) {
            validateAccountId(accountId);
            final ExecutionState desiredState = current.getOrDefault(accountId, ExecutionState.RUN);
            emitted.put(accountId, desiredState);
            events.add(buildRiskDecision(accountId, desiredState, reasonFor(accountId, desiredState)));
        }

        lastEmittedStates.set(emitted);
        return List.copyOf(events);
    }

    public Map<String, ExecutionState> currentStates() {
        return Collections.unmodifiableMap(accountStates.get());
    }

    public Map<String, java.math.BigDecimal> exposureSnapshot() {
        return Collections.unmodifiableMap(exposureByAccount.get());
    }

    private void setAccountState(final String accountId, final ExecutionState desiredState) {
        final Map<String, ExecutionState> updated = new HashMap<>(accountStates.get());
        updated.put(accountId, desiredState);
        accountStates.set(updated);
    }

    private void bindCanonicalToAccount(final String accountId, final CanonicalId canonicalId) {
        if (canonicalId == null) {
            return;
        }
        final Map<String, CanonicalId> updated = new HashMap<>(canonicalByAccount.get());
        updated.put(accountId, canonicalId);
        canonicalByAccount.set(updated);
    }

    private void recomputeExposure(final String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        final CanonicalId canonicalId = canonicalByAccount.get().get(accountId);
        final java.math.BigDecimal exposure = canonicalId == null
                ? java.math.BigDecimal.ZERO
                : exposureLedger.netPosition(canonicalId).abs().multiply(exposureLedger.averageEntryPrice(canonicalId));

        final Map<String, java.math.BigDecimal> updated = new HashMap<>(exposureByAccount.get());
        updated.put(accountId, exposure);
        exposureByAccount.set(updated);
    }

    private String resolveAccountId(final CanonicalId canonicalId) {
        if (canonicalId == null) {
            return "unknown";
        }
        for (Map.Entry<String, CanonicalId> entry : canonicalByAccount.get().entrySet()) {
            if (canonicalId.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return canonicalId.format();
    }

    private RiskDecisionEvent buildRiskDecision(final String accountId,
                                                final ExecutionState desiredState,
                                                final String reason) {
        final String payloadText = String.join("|", accountId, desiredState.name(), reason);
        final byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
        final UUID eventId = UUID.randomUUID();
        final EventEnvelope envelope = new EventEnvelope(
                eventId,
                EventType.RiskDecisionEvent,
                null,
                null,
                null,
                "risk-decision:" + accountId + ":" + desiredState.name() + ":" + reason,
                0L,
                Instant.now(),
                (byte) 2,
                payload,
                sourceProcessId,
                null
        );
        return new RiskDecisionEvent(accountId, desiredState, reason, envelope);
    }

    private String reasonFor(final String accountId, final ExecutionState desiredState) {
        if (desiredState == ExecutionState.SAFE && accountStates.get().get(accountId) == ExecutionState.SAFE) {
            return REASON_ORDER_UNKNOWN;
        }
        return REASON_DEFAULT_RUN + ":exposure=" + exposureByAccount.get().getOrDefault(accountId, java.math.BigDecimal.ZERO).toPlainString();
    }

    private boolean isHalted(final String accountId) {
        return accountStates.get().getOrDefault(accountId, ExecutionState.RUN) == ExecutionState.HALT;
    }

    private void trackUnknownAndMaybeEscalate(final String accountId, final CanonicalId canonicalId) {
        final Instant now = timeProvider.eventTime();
        final Instant windowStart = now.minus(haltWindowDuration);
        final Map<CanonicalId, Deque<Instant>> updated = new HashMap<>(unknownTimestamps.get());
        final Deque<Instant> deque = new ArrayDeque<>(updated.getOrDefault(canonicalId, new ArrayDeque<>()));
        deque.addLast(now);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(windowStart)) {
            deque.removeFirst();
        }
        if (deque.size() >= haltThresholdCount
                && accountStates.get().getOrDefault(accountId, ExecutionState.RUN) == ExecutionState.SAFE) {
            setAccountState(accountId, ExecutionState.HALT);
        }
        updated.put(canonicalId, deque);
        unknownTimestamps.set(updated);
    }

    private void clearUnknownWindow(final CanonicalId canonicalId) {
        final Map<CanonicalId, Deque<Instant>> updated = new HashMap<>(unknownTimestamps.get());
        updated.remove(canonicalId);
        unknownTimestamps.set(updated);
    }

    public void executeResetCommand(final RiskStateResetCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        final String accountId = resolveAccountId(command.canonicalId());
        final ExecutionState currentState = accountStates.get().getOrDefault(accountId, ExecutionState.RUN);
        if (currentState != ExecutionState.SAFE && currentState != ExecutionState.HALT) {
            LOGGER.warning(() -> "Reset from state " + currentState + " ignored for accountId=" + accountId);
            return;
        }
        setAccountState(accountId, ExecutionState.RUN);
        clearUnknownWindow(command.canonicalId());
        LOGGER.info(() -> "Manual risk reset to RUN for accountId=" + accountId
                + ", operatorId=" + command.operatorId() + ", reason=" + command.reason());
    }

    private static void validateAccountId(final String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
    }
}
