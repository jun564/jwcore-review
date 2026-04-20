package org.jwcore.riskcoordinator.emitter;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.core.time.ControllableTimeProvider;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.execution.common.emit.EventEmitter;
import org.jwcore.execution.common.state.ExecutionState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskDecisionEmitterTest {
    @Test
    void shouldEmitDecisionOnStateChange() {
        final var journal = new LocalInMemoryEventJournal();
        final var emitter = new RiskDecisionEmitter(
                new EventEmitter(journal, new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z")), "risk-coordinator-1"),
                new BigDecimal("100"),
                new BigDecimal("150"));

        emitter.emitChanges(Map.of("crypto-account", ExecutionState.RUN));
        emitter.setExposureByAccount(Map.of("crypto-account", new BigDecimal("120")));
        emitter.emitChanges(Map.of("crypto-account", ExecutionState.SAFE));

        assertEquals(2, journal.count(EventType.RiskDecisionEvent));
    }

    @Test
    void shouldNotEmitWhenStateUnchanged() {
        final var journal = new LocalInMemoryEventJournal();
        final var emitter = new RiskDecisionEmitter(
                new EventEmitter(journal, new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z")), "risk-coordinator-1"),
                new BigDecimal("100"),
                new BigDecimal("150"));

        emitter.emitChanges(Map.of("crypto-account", ExecutionState.SAFE));
        final int before = journal.size();
        emitter.emitChanges(Map.of("crypto-account", ExecutionState.SAFE));

        assertEquals(before, journal.size());
    }

    @Test
    void shouldEmitMultipleAccountsIndependently() {
        final var journal = new LocalInMemoryEventJournal();
        final var emitter = new RiskDecisionEmitter(
                new EventEmitter(journal, new ControllableTimeProvider(1L, Instant.parse("2026-04-19T08:00:00Z")), "risk-coordinator-1"),
                new BigDecimal("100"),
                new BigDecimal("150"));

        emitter.emitChanges(Map.of("crypto-account", ExecutionState.RUN, "forex-account", ExecutionState.RUN));
        final Map<String, ExecutionState> changed = new LinkedHashMap<>();
        changed.put("crypto-account", ExecutionState.SAFE);
        changed.put("forex-account", ExecutionState.RUN);
        emitter.emitChanges(changed);

        assertEquals(3, journal.count(EventType.RiskDecisionEvent));
    }

    private static final class LocalInMemoryEventJournal implements IEventJournal {
        private final List<EventEnvelope> events = new CopyOnWriteArrayList<>();

        @Override
        public void append(final EventEnvelope envelope) {
            events.add(envelope);
        }

        @Override
        public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) {
            return events.stream().filter(e -> !e.timestampEvent().isBefore(fromInclusive) && e.timestampEvent().isBefore(toExclusive)).toList();
        }

        @Override
        public TailSubscription tail(final java.util.function.Consumer<EventEnvelope> consumer) {
            return () -> { };
        }

        private int size() {
            return events.size();
        }

        private int count(final EventType type) {
            return (int) events.stream().filter(e -> e.eventType() == type).count();
        }
    }
}
