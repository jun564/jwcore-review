package org.jwcore.execution.forex.runtime;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.time.ITimeProvider;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.Instrument;
import org.jwcore.domain.OrderIntent;
import org.jwcore.domain.RejectReason;
import org.jwcore.execution.common.emit.EventEmitter;
import org.jwcore.execution.common.events.RiskDecisionEvent;
import org.jwcore.execution.common.registry.IntentRegistry;
import org.jwcore.execution.common.runtime.PendingIntent;
import org.jwcore.execution.common.state.ExecutionState;
import org.jwcore.execution.common.state.ExecutionStateResolver;
import org.jwcore.execution.forex.broker.BrokerSession;
import org.jwcore.execution.forex.risk.LocalRiskPolicy;
import org.jwcore.execution.forex.risk.LocalRiskSnapshot;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ExecutionRuntime {
    private final ExecutionRuntimeConfig config;
    private final IEventJournal eventJournal;
    private final ITimeProvider timeProvider;
    private final BrokerSession brokerSession;
    private final LocalRiskPolicy localRiskPolicy;
    private final EventEmitter eventEmitter;
    private final IntentRegistry intentRegistry;
    private final ExecutionStateResolver stateResolver = new ExecutionStateResolver();
    private final Set<UUID> processedEventIds;

    private ExecutionState currentState = ExecutionState.RUN;
    private Instant lastReadTimestamp = Instant.EPOCH;
    private int cycleCounter = 0;

    public ExecutionRuntime(final ExecutionRuntimeConfig config,
                            final IEventJournal eventJournal,
                            final ITimeProvider timeProvider,
                            final BrokerSession brokerSession,
                            final LocalRiskPolicy localRiskPolicy) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.eventJournal = Objects.requireNonNull(eventJournal, "eventJournal cannot be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider cannot be null");
        this.brokerSession = Objects.requireNonNull(brokerSession, "brokerSession cannot be null");
        this.localRiskPolicy = Objects.requireNonNull(localRiskPolicy, "localRiskPolicy cannot be null");
        this.eventEmitter = new EventEmitter(eventJournal, timeProvider, config.nodeId());
        this.intentRegistry = new IntentRegistry(timeProvider, eventEmitter);
        this.processedEventIds = java.util.Collections.newSetFromMap(new BoundedUuidMap(config.processedEventsCapacity()));
    }

    public void tickCycle() {
        final Instant now = timeProvider.eventTime();
        final List<EventEnvelope> events = eventJournal.read(lastReadTimestamp, now.plusNanos(1));
        final List<EventEnvelope> freshEvents = events.stream().filter(event -> processedEventIds.add(event.eventId())).toList();

        final ExecutionState globalDecision = resolveGlobalDecision(freshEvents);
        final ExecutionState localDecision = Objects.requireNonNullElse(
                localRiskPolicy.evaluate(new LocalRiskSnapshot(config.accountId(), currentState, brokerSession.isConnected())),
                ExecutionState.RUN);
        final ExecutionState targetState = stateResolver.resolve(localDecision, globalDecision);
        applyState(targetState);

        intentRegistry.absorb(freshEvents);
        processOrderIntents(freshEvents);

        intentRegistry.checkTimeouts();

        cycleCounter++;
        if (cycleCounter % config.marginEmitEveryNCycles() == 0) {
            eventEmitter.emit(eventEmitter.createMarginUpdateEvent(
                    config.accountId(),
                    brokerSession.currentMarginLevel(),
                    brokerSession.currentFreeMargin(),
                    brokerSession.currentEquity()).envelope());
        }

        lastReadTimestamp = now;
    }

    private ExecutionState resolveGlobalDecision(final List<EventEnvelope> freshEvents) {
        ExecutionState decision = ExecutionState.RUN;
        for (final EventEnvelope envelope : freshEvents) {
            if (envelope.eventType() != EventType.RiskDecisionEvent) {
                continue;
            }
            final RiskDecisionEvent riskDecisionEvent = eventEmitter.parseRiskDecisionEvent(envelope);
            if (riskDecisionEvent.appliesTo(config.accountId())) {
                decision = decision.moreRestrictive(riskDecisionEvent.desiredState());
            }
        }
        return decision;
    }

    private void processOrderIntents(final List<EventEnvelope> freshEvents) {
        for (final EventEnvelope envelope : freshEvents) {
            if (envelope.eventType() != EventType.OrderIntentEvent) {
                continue;
            }
            final UUID intentId = UUID.fromString(Objects.requireNonNull(envelope.localIntentId(), "localIntentId cannot be null"));
            if (intentRegistry.findCanonicalId(intentId).isPresent()) {
                continue;
            }
            if (intentRegistry.isTerminated(envelope.correlationId())) {
                continue;
            }
            final OrderIntent orderIntent = parseOrderIntent(envelope);
            if (currentState == ExecutionState.RUN) {
                brokerSession.submit(orderIntent);
                intentRegistry.bind(intentId, orderIntent.canonicalId(), config.accountId(), envelope.timestampEvent(), config.orderTimeout().toMillis());
                continue;
            }
            if (currentState == ExecutionState.SAFE) {
                emitRejected(orderIntent, envelope, RejectReason.SAFE_STATE);
                continue;
            }
            if (currentState == ExecutionState.HALT) {
                emitRejected(orderIntent, envelope, RejectReason.HALT_STATE);
                continue;
            }
        }
    }


    private void emitRejected(final OrderIntent orderIntent, final EventEnvelope envelope, final RejectReason reason) {
        final PendingIntent pendingIntent = new PendingIntent(
                UUID.fromString(Objects.requireNonNull(envelope.localIntentId(), "localIntentId cannot be null")),
                orderIntent.canonicalId(),
                config.accountId(),
                envelope.timestampEvent(),
                config.orderTimeout().toMillis()
        );
        eventEmitter.emitOrderRejected(pendingIntent, reason);
    }

    private OrderIntent parseOrderIntent(final EventEnvelope envelope) {
        final String payloadText = new String(envelope.payload(), StandardCharsets.UTF_8);
        final String[] parts = payloadText.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid OrderIntentEvent payload");
        }
        return new OrderIntent(
                Objects.requireNonNull(envelope.canonicalId(), "canonicalId cannot be null"),
                new Instrument(parts[0]),
                Double.parseDouble(parts[1])
        );
    }

    public void markTerminal(final UUID intentId) {
        intentRegistry.remove(intentId);
    }

    public ExecutionState currentState() {
        return currentState;
    }

    public int pendingIntents() {
        return intentRegistry.size();
    }

    public int processedEvents() {
        return processedEventIds.size();
    }

    private void applyState(final ExecutionState targetState) {
        if (currentState.canTransitionTo(targetState)) {
            currentState = targetState;
        }
    }

    private static final class BoundedUuidMap extends LinkedHashMap<UUID, Boolean> {
        private final int maxEntries;

        private BoundedUuidMap(final int maxEntries) {
            super(16, 0.75f, true);
            this.maxEntries = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(final Map.Entry<UUID, Boolean> eldest) {
            return size() > maxEntries;
        }
    }
}
