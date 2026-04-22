package org.jwcore.riskcoordinator.app;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.core.shutdown.GracefulShutdownCoordinator;
import org.jwcore.core.time.ControllableTimeProvider;
import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.jwcore.domain.events.OrderUnknownEvent;
import org.jwcore.execution.common.emit.EventEmitter;
import org.jwcore.execution.common.state.ExecutionState;
import org.jwcore.riskcoordinator.config.RiskCoordinatorConfig;
import org.jwcore.riskcoordinator.engine.RiskCoordinatorEngine;
import org.jwcore.riskcoordinator.tailer.RiskCoordinatorTailer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {
    @Test
    void shouldPerformRebuildBeforeInitialPublish() throws Exception {
        final RecordingJournal business = new RecordingJournal();
        final RecordingJournal market = new RecordingJournal();
        business.append(unknownEnvelope("crypto"));
        final Main.Application app = application(business, market);

        app.start();
        Thread.sleep(120L);

        final EventEmitter parser = new EventEmitter(business, new ControllableTimeProvider(1L, Instant.parse("2026-04-20T10:00:00Z")), "risk-node-1");
        final var firstDecision = parser.parseRiskDecisionEvent(business.emittedRisk().get(0));
        assertEquals(ExecutionState.SAFE, firstDecision.desiredState());
        app.coordinator().execute();
    }

    @Test
    void shouldEmitInitialPublishBeforeStartingScheduler() throws Exception {
        final RecordingJournal business = new RecordingJournal();
        final RecordingJournal market = new RecordingJournal();
        final Main.Application app = application(business, market);

        app.start();
        Thread.sleep(30L);

        assertTrue(business.emittedRisk().size() >= 1);
        app.coordinator().execute();
    }

    @Test
    void shouldEmitRiskDecisionAfterStateTransition() throws Exception {
        final RecordingJournal business = new RecordingJournal();
        final RecordingJournal market = new RecordingJournal();
        final Main.Application app = application(business, market);
        app.start();
        Thread.sleep(100L);

        business.append(unknownEnvelope("crypto"));
        Thread.sleep(150L);

        final EventEmitter parser = new EventEmitter(business, new ControllableTimeProvider(1L, Instant.parse("2026-04-20T10:00:00Z")), "risk-node-1");
        final List<EventEnvelope> emitted = business.emittedRisk();
        assertTrue(emitted.size() >= 2);
        final var last = parser.parseRiskDecisionEvent(emitted.get(emitted.size() - 1));
        assertEquals(ExecutionState.SAFE, last.desiredState());
        app.coordinator().execute();
    }

    private static Main.Application application(final RecordingJournal business, final RecordingJournal market) {
        final RiskCoordinatorConfig config = new RiskCoordinatorConfig(
                new BigDecimal("100"),
                new BigDecimal("150"),
                50L,
                "risk-node-1",
                List.of("crypto", "forex")
        );
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(config.nodeId());
        final EventEmitter emitter = new EventEmitter(business, new ControllableTimeProvider(1L, Instant.parse("2026-04-20T10:00:00Z")), config.nodeId());
        final RiskCoordinatorTailer tailer = new RiskCoordinatorTailer(business, market);
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        final GracefulShutdownCoordinator coordinator = new GracefulShutdownCoordinator(Duration.ofSeconds(1), thread -> { });
        coordinator.register(new org.jwcore.core.shutdown.GracefulShutdownParticipant() {
            @Override public String name() { return "test"; }
            @Override public void flush() { tailer.close(); scheduler.shutdownNow(); }
            @Override public void snapshot() { }
        });
        return new Main.Application(engine, emitter, tailer, config, scheduler, coordinator);
    }

    private static EventEnvelope unknownEnvelope(final String accountId) {
        final OrderUnknownEvent event = new OrderUnknownEvent(accountId, UUID.randomUUID().toString(), "UNKNOWN", Instant.parse("2026-04-20T10:00:00Z"), null);
        final byte[] payload = event.toPayload();
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.OrderUnknownEvent,
                null,
                UUID.randomUUID().toString(),
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                IdempotencyKeys.generate(null, EventType.OrderUnknownEvent, payload),
                1L,
                Instant.parse("2026-04-20T10:00:00Z"),
                (byte) 1,
                payload,
                "test-source",
                UUID.randomUUID()
        );
    }

    private static final class RecordingJournal implements IEventJournal {
        private final List<EventEnvelope> events = new CopyOnWriteArrayList<>();
        private final AtomicLong sequence = new AtomicLong(0L);

        @Override
        public long append(final EventEnvelope envelope) {
            final long next = sequence.incrementAndGet();
            events.add(withSequence(envelope, next));
            return next;
        }

        @Override
        public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) {
            return events.stream()
                    .filter(e -> !e.timestampEvent().isBefore(fromInclusive) && e.timestampEvent().isBefore(toExclusive))
                    .toList();
        }

        @Override
        public long currentSequence() {
            return sequence.get();
        }

        @Override
        public List<EventEnvelope> readAfterSequence(final long since) {
            return events.stream().filter(e -> e.timestampMono() > since)
                    .sorted(java.util.Comparator.comparingLong(EventEnvelope::timestampMono))
                    .toList();
        }

        @Override
        public TailSubscription tail(final java.util.function.Consumer<EventEnvelope> consumer) {
            return () -> { };
        }

        private List<EventEnvelope> emittedRisk() {
            final List<EventEnvelope> result = new ArrayList<>();
            for (final EventEnvelope envelope : events) {
                if (envelope.eventType() == EventType.RiskDecisionEvent) {
                    result.add(envelope);
                }
            }
            return result;
        }

        private static EventEnvelope withSequence(final EventEnvelope envelope, final long seq) {
            return new EventEnvelope(envelope.eventId(), envelope.eventType(), envelope.brokerOrderId(), envelope.localIntentId(),
                    envelope.canonicalId(), envelope.idempotencyKey(), seq, envelope.timestampEvent(), envelope.payloadVersion(),
                    envelope.payload(), envelope.sourceProcessId(), envelope.correlationId());
        }
    }
}
