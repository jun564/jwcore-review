package org.jwcore.riskcoordinator.engine;

import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.events.OrderCanceledEvent;
import org.jwcore.domain.events.OrderFilledEvent;
import org.jwcore.domain.events.OrderRejectedEvent;
import org.jwcore.domain.events.OrderSubmittedEvent;
import org.jwcore.domain.events.OrderUnknownEvent;
import org.jwcore.execution.common.events.RiskDecisionEvent;
import org.jwcore.execution.common.state.ExecutionState;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
    private final AtomicReference<Map<String, ExecutionState>> accountStates = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, ExecutionState>> lastEmittedStates = new AtomicReference<>(Map.of());

    public RiskCoordinatorEngine(final String sourceProcessId) {
        this(new ExposureLedger(), sourceProcessId);
    }

    public RiskCoordinatorEngine(final ExposureLedger exposureLedger, final String sourceProcessId) {
        this.exposureLedger = Objects.requireNonNull(exposureLedger, "exposureLedger cannot be null");
        this.sourceProcessId = Objects.requireNonNull(sourceProcessId, "sourceProcessId cannot be null");
        if (sourceProcessId.isBlank()) {
            throw new IllegalArgumentException("sourceProcessId cannot be blank");
        }
    }

    public Set<String> apply(final EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        try {
            if (envelope.eventType() == EventType.OrderSubmittedEvent) {
                final OrderSubmittedEvent event = OrderSubmittedEvent.fromPayload(envelope.payload());
                exposureLedger.add(event.accountId(), BigDecimal.valueOf(event.size()));
                return Set.of(event.accountId());
            }
            if (envelope.eventType() == EventType.OrderFilledEvent) {
                final OrderFilledEvent event = OrderFilledEvent.fromPayload(envelope.payload());
                final boolean clamped = exposureLedger.subtract(event.accountId(), event.size());
                if (clamped) {
                    LOGGER.warning(() -> "Clamped exposure to ZERO after OrderFilledEvent, accountId=" + event.accountId()
                            + ", eventId=" + envelope.eventId());
                }
                return Set.of(event.accountId());
            }
            if (envelope.eventType() == EventType.OrderCanceledEvent) {
                final OrderCanceledEvent event = OrderCanceledEvent.fromPayload(envelope.payload());
                final boolean clamped = exposureLedger.subtract(event.accountId(), event.size());
                if (clamped) {
                    LOGGER.warning(() -> "Clamped exposure to ZERO after OrderCanceledEvent, accountId=" + event.accountId()
                            + ", eventId=" + envelope.eventId());
                }
                return Set.of(event.accountId());
            }
            if (envelope.eventType() == EventType.OrderRejectedEvent) {
                final OrderRejectedEvent event = OrderRejectedEvent.fromPayload(envelope.payload());
                // DŁUG-310: MVP 3B nie rozlicza ekspozycji dla rejected, bo payload nie niesie size.
                return Set.of(event.accountId());
            }
            if (envelope.eventType() == EventType.OrderUnknownEvent) {
                final OrderUnknownEvent event = OrderUnknownEvent.fromPayload(envelope.payload());
                setAccountState(event.accountId(), ExecutionState.SAFE);
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

    public Map<String, BigDecimal> exposureSnapshot() {
        return exposureLedger.snapshot();
    }

    private void setAccountState(final String accountId, final ExecutionState desiredState) {
        final Map<String, ExecutionState> updated = new HashMap<>(accountStates.get());
        updated.put(accountId, desiredState);
        accountStates.set(updated);
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
        return REASON_DEFAULT_RUN + ":exposure=" + exposureLedger.exposureOf(accountId).toPlainString();
    }

    private static void validateAccountId(final String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
    }
}
