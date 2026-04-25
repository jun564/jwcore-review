package org.jwcore.riskcoordinator.engine;

import org.jwcore.core.time.ITimeProvider;
import org.jwcore.core.time.RealTimeProvider;
import org.jwcore.core.failure.ProcessingFailureEmitter;
import org.jwcore.core.util.FailureCounter;
import org.jwcore.core.ports.IEventJournal;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.events.AlertEvent;
import org.jwcore.domain.events.AlertSeverity;
import org.jwcore.domain.events.AlertType;
import org.jwcore.domain.events.EventProcessingFailedEvent;
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
import java.util.stream.Collectors;
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
import java.util.logging.Level;
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
    private final AlertEnvelopeFactory alertEnvelopeFactory;
    private final ProcessingFailureEmitter failureEmitter;
    private final FailureCounter failureCounter;
    private final IEventJournal alertJournal;

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
        this(exposureLedger, sourceProcessId, timeProvider, haltThresholdCount, haltWindowDuration, null, null, null, false);
    }

    public RiskCoordinatorEngine(final ExposureLedger exposureLedger,
                                 final String sourceProcessId,
                                 final ITimeProvider timeProvider,
                                 final int haltThresholdCount,
                                 final Duration haltWindowDuration,
                                 final ProcessingFailureEmitter failureEmitter) {
        this(exposureLedger, sourceProcessId, timeProvider, haltThresholdCount, haltWindowDuration,
                Objects.requireNonNull(failureEmitter, "failureEmitter cannot be null"), null, null, true);
    }


    public RiskCoordinatorEngine(final ExposureLedger exposureLedger,
                                 final String sourceProcessId,
                                 final ITimeProvider timeProvider,
                                 final int haltThresholdCount,
                                 final Duration haltWindowDuration,
                                 final ProcessingFailureEmitter failureEmitter,
                                 final FailureCounter failureCounter,
                                 final IEventJournal alertJournal) {
        this(exposureLedger, sourceProcessId, timeProvider, haltThresholdCount, haltWindowDuration,
                Objects.requireNonNull(failureEmitter, "failureEmitter cannot be null"),
                Objects.requireNonNull(failureCounter, "failureCounter cannot be null"),
                Objects.requireNonNull(alertJournal, "alertJournal cannot be null"),
                true);
    }

    private RiskCoordinatorEngine(final ExposureLedger exposureLedger,
                                  final String sourceProcessId,
                                  final ITimeProvider timeProvider,
                                  final int haltThresholdCount,
                                  final Duration haltWindowDuration,
                                  final ProcessingFailureEmitter failureEmitter,
                                  final FailureCounter failureCounter,
                                  final IEventJournal alertJournal,
                                  final boolean emitterRequired) {
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
        this.alertEnvelopeFactory = new AlertEnvelopeFactory(sourceProcessId);
        this.failureEmitter = emitterRequired
                ? Objects.requireNonNull(failureEmitter, "failureEmitter cannot be null")
                : failureEmitter;
        this.failureCounter = failureCounter;
        this.alertJournal = alertJournal;
    }

    public Set<String> apply(final EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        String extractedAccountId = null;  // ADR-017 Faza 2: kontekst zachowany przed wyjatkiem
        // ADR-017 Faza 1: copy-before-commit for RiskCoordinatorEngine-owned maps.
        // Mutations of accountStates, canonicalByAccount, exposureByAccount and
        // unknownTimestamps must be committed via AtomicReference.set() only after
        // local computation succeeds. ExposureLedger transactional hardening is out of scope.
        try {
            if (envelope.eventType() == EventType.OrderSubmittedEvent) {
                final OrderSubmittedEvent event = OrderSubmittedEvent.fromPayload(envelope.payload());
                extractedAccountId = event.accountId();
                bindCanonicalToAccount(event.accountId(), event.canonicalId());
                recomputeExposure(event.accountId());
                return Set.of(event.accountId());
            }
            if (envelope.eventType() == EventType.OrderIntentEvent) {
                final OrderIntentEvent event = OrderIntentEvent.fromPayload(envelope.payload());
                final String accountId = event.accountId();
                extractedAccountId = accountId;
                if (isHalted(accountId)) {
                    return Set.of(accountId);
                }
                exposureLedger.apply(envelope);
                return Set.of(accountId);
            }
            if (envelope.eventType() == EventType.OrderFilledEvent) {
                final OrderFilledEvent event = OrderFilledEvent.fromPayload(envelope.payload());
                final String accountId = resolveAccountId(event.canonicalId());
                extractedAccountId = accountId;
                if (isHalted(accountId)) {
                    return Set.of(accountId);
                }
                // ADR-017 Faza 2: copy-before-commit - mapy modyfikowane lokalnie, commit po sukcesie
                final Map<String, CanonicalId> nextCanonical = new HashMap<>(canonicalByAccount.get());
                nextCanonical.put(accountId, event.canonicalId());
                exposureLedger.apply(envelope);  // moze rzucic - mapy jeszcze nie commitowane
                final java.math.BigDecimal exposure = calculateExposure(nextCanonical, accountId);
                canonicalByAccount.set(Collections.unmodifiableMap(nextCanonical));
                final Map<String, java.math.BigDecimal> nextExposure = new HashMap<>(exposureByAccount.get());
                nextExposure.put(accountId, exposure);
                exposureByAccount.set(Collections.unmodifiableMap(nextExposure));
                return Set.of(accountId);
            }
            if (envelope.eventType() == EventType.OrderCanceledEvent) {
                final OrderCanceledEvent event = OrderCanceledEvent.fromPayload(envelope.payload());
                final String accountId = resolveAccountId(event.canonicalId());
                extractedAccountId = accountId;
                if (isHalted(accountId)) {
                    return Set.of(accountId);
                }
                // ADR-017 Faza 2: copy-before-commit
                final Map<String, CanonicalId> nextCanonical = new HashMap<>(canonicalByAccount.get());
                nextCanonical.put(accountId, event.canonicalId());
                exposureLedger.apply(envelope);
                final java.math.BigDecimal exposure = calculateExposure(nextCanonical, accountId);
                canonicalByAccount.set(Collections.unmodifiableMap(nextCanonical));
                final Map<String, java.math.BigDecimal> nextExposure = new HashMap<>(exposureByAccount.get());
                nextExposure.put(accountId, exposure);
                exposureByAccount.set(Collections.unmodifiableMap(nextExposure));
                return Set.of(accountId);
            }
            if (envelope.eventType() == EventType.OrderRejectedEvent) {
                final OrderRejectedEvent event = OrderRejectedEvent.fromPayload(envelope.payload());
                final String accountId = envelope.canonicalId() == null ? event.accountId() : resolveAccountId(envelope.canonicalId());
                extractedAccountId = accountId;
                if (isHalted(accountId)) {
                    return Set.of(accountId);
                }
                exposureLedger.apply(envelope);
                recomputeExposure(accountId);
                return Set.of(accountId);
            }
            if (envelope.eventType() == EventType.OrderUnknownEvent) {
                final OrderUnknownEvent event = OrderUnknownEvent.fromPayload(envelope.payload());
                extractedAccountId = event.accountId();
                setAccountState(event.accountId(), ExecutionState.SAFE);
                final CanonicalId canonicalId = canonicalByAccount.get().get(event.accountId());
                if (canonicalId != null) {
                    trackUnknownAndMaybeEscalate(event.accountId(), canonicalId);
                }
                return Set.of(event.accountId());
            }
            return Set.of();
        } catch (final Exception exception) {
            LOGGER.log(Level.SEVERE,
                    "Nieudane przetworzenie eventu w RiskCoordinatorEngine eventId="
                            + envelope.eventId() + ", eventType=" + envelope.eventType(),
                    exception);
            if (failureEmitter != null) {
                try {
                    final int attemptNumber = failureCounter == null ? 1 : failureCounter.nextAttemptNumber(envelope.eventId());
                    final String failedAccountId = resolveFailureAccountId(extractedAccountId, envelope);
                    final String originalEventType = envelope.eventType() == null ? null : envelope.eventType().name();
                    final EventProcessingFailedEvent failure = failureEmitter.emit(
                            envelope.eventId(),
                            exception,
                            attemptNumber,
                            "risk-coordinator",
                            originalEventType,
                            failedAccountId
                    );
                    if (failure.isPermanent()) {
                        escalateToSafe(failedAccountId, failure);
                    }
                } catch (final Exception emitException) {
                    LOGGER.log(Level.SEVERE, "Failed to emit/escalate EventProcessingFailedEvent", emitException);
                }
            }
            return Set.of();
        }
    }

    public Optional<RiskDecisionEvent> evaluateAndBuildIfChanged(final String accountId) {
        return evaluateAndBuildResultIfChanged(accountId)
                .decisionEnvelope()
                .map(envelope -> {
                    final String[] parts = new String(envelope.payload(), StandardCharsets.UTF_8).split("\\|", 3);
                    return new RiskDecisionEvent(parts[0], ExecutionState.valueOf(parts[1]), parts[2], envelope);
                });
    }

    public RiskEvaluationResult evaluateAndBuildResultIfChanged(final String accountId) {
        validateAccountId(accountId);
        final ExecutionState desiredState = accountStates.get().getOrDefault(accountId, ExecutionState.RUN);
        final ExecutionState previous = lastEmittedStates.get().get(accountId);
        if (desiredState == previous) {
            return RiskEvaluationResult.empty();
        }

        final Map<String, ExecutionState> updated = new HashMap<>(lastEmittedStates.get());
        updated.put(accountId, desiredState);
        lastEmittedStates.set(updated);

        final String reason = reasonFor(accountId, desiredState);
        final RiskDecisionEvent decision = buildRiskDecision(accountId, desiredState, reason);
        final AlertEvent alert = buildAlertEvent(accountId, previous, desiredState, reason);

        final EventEnvelope alertEnvelope = alertEnvelopeFactory.buildEnvelope(alert);
        final EventEnvelope decisionEnvelope = decision.envelope();
        return new RiskEvaluationResult(Optional.of(decisionEnvelope), Optional.of(alertEnvelope));
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


    private AlertEvent buildAlertEvent(final String accountId,
                                       final ExecutionState from,
                                       final ExecutionState to,
                                       final String reason) {
        return new AlertEvent(
                UUID.randomUUID(),
                accountId,
                mapToSeverity(to),
                from == null ? null : org.jwcore.domain.ExecutionState.valueOf(from.name()),
                org.jwcore.domain.ExecutionState.valueOf(to.name()),
                AlertType.STATE_TRANSITION,
                reason,
                List.of(),
                timeProvider.eventTime()
        );
    }

    private static AlertSeverity mapToSeverity(final ExecutionState state) {
        return switch (state) {
            case RUN -> AlertSeverity.INFO;
            case SAFE -> AlertSeverity.WARNING;
            case HALT, KILL -> AlertSeverity.CRITICAL;
        };
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


    private void escalateToSafe(final String failedAccountId, final EventProcessingFailedEvent failure) {
        final Map<String, ExecutionState> nextStates = new HashMap<>(accountStates.get());
        final List<String> affectedAccounts = new ArrayList<>();

        if (isKnownAccountId(failedAccountId)) {
            nextStates.put(failedAccountId, ExecutionState.SAFE);
            affectedAccounts.add(failedAccountId);
        } else {
            // Nieznany kontekst -> wszystkie znane konta modulu (zasada 37)
            for (final String accountId : nextStates.keySet()) {
                nextStates.put(accountId, ExecutionState.SAFE);
                affectedAccounts.add(accountId);
            }
        }

        accountStates.set(Collections.unmodifiableMap(nextStates));

        if (alertJournal != null) {
            final AlertEvent alert = buildPermanentFailureAlert(
                    isKnownAccountId(failedAccountId) ? failedAccountId : null,
                    affectedAccounts,
                    failure);
            alertJournal.append(alertEnvelopeFactory.buildEnvelope(alert));
        }
    }

    private boolean isKnownAccountId(final String accountId) {
        return accountId != null
                && !accountId.isBlank()
                && !"unknown".equalsIgnoreCase(accountId);
    }

    private String resolveFailureAccountId(final String extractedAccountId, final EventEnvelope envelope) {
        if (extractedAccountId != null && !extractedAccountId.isBlank()) {
            return extractedAccountId;
        }
        return tryExtractAccountId(envelope);
    }

    private java.math.BigDecimal calculateExposure(final Map<String, CanonicalId> canonicalSnapshot,
                                                   final String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return java.math.BigDecimal.ZERO;
        }
        final CanonicalId canonicalId = canonicalSnapshot.get(accountId);
        if (canonicalId == null) {
            return java.math.BigDecimal.ZERO;
        }
        return exposureLedger.netPosition(canonicalId).abs().multiply(exposureLedger.averageEntryPrice(canonicalId));
    }

    Map<String, CanonicalId> canonicalSnapshot() { // test visibility - ADR-017 Faza 2
        return Collections.unmodifiableMap(canonicalByAccount.get());
    }

    private AlertEvent buildPermanentFailureAlert(final String failedAccountId,
                                                  final List<String> affectedAccounts,
                                                  final EventProcessingFailedEvent failure) {
        final String accountId = failedAccountId == null ? "unknown" : failedAccountId;
        final List<String> compactAffected = affectedAccounts.stream().distinct().limit(20).collect(Collectors.toList());
        return new AlertEvent(
                UUID.randomUUID(),
                accountId,
                AlertSeverity.CRITICAL,
                org.jwcore.domain.ExecutionState.SAFE,
                org.jwcore.domain.ExecutionState.SAFE,
                AlertType.PERMANENT_FAILURE,
                failure.errorType(),
                List.of(),
                timeProvider.eventTime(),
                compactAffected
        );
    }

    private String tryExtractAccountId(final EventEnvelope envelope) {
        try {
            return switch (envelope.eventType()) {
                case OrderSubmittedEvent -> OrderSubmittedEvent.fromPayload(envelope.payload()).accountId();
                case OrderIntentEvent -> OrderIntentEvent.fromPayload(envelope.payload()).accountId();
                case OrderUnknownEvent -> OrderUnknownEvent.fromPayload(envelope.payload()).accountId();
                case OrderRejectedEvent -> OrderRejectedEvent.fromPayload(envelope.payload()).accountId();
                default -> null;
            };
        } catch (final Exception ignored) {
            return null;
        }
    }

    private static void validateAccountId(final String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
    }
}
