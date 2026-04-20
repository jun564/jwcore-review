package org.jwcore.riskcoordinator.app;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.shutdown.GracefulShutdownCoordinator;
import org.jwcore.core.shutdown.GracefulShutdownParticipant;
import org.jwcore.core.time.RealTimeProvider;
import org.jwcore.execution.common.emit.EventEmitter;
import org.jwcore.riskcoordinator.app.support.InMemoryEventJournal;
import org.jwcore.riskcoordinator.config.RiskCoordinatorConfig;
import org.jwcore.riskcoordinator.config.RiskCoordinatorPropertiesLoader;
import org.jwcore.riskcoordinator.emitter.RiskDecisionEmitter;
import org.jwcore.riskcoordinator.engine.RiskCoordinatorEngine;
import org.jwcore.riskcoordinator.tailer.RiskCoordinatorTailer;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main {
    private Main() {}

    public static void main(final String[] args) throws Exception {
        final Application application = bootstrap(new InMemoryEventJournal(), new InMemoryEventJournal());
        application.start();
    }

    public static Application bootstrap(final IEventJournal eventsBusinessJournal, final IEventJournal marketDataJournal) throws Exception {
        Objects.requireNonNull(eventsBusinessJournal, "eventsBusinessJournal cannot be null");
        Objects.requireNonNull(marketDataJournal, "marketDataJournal cannot be null");
        final InputStream inputStream = Main.class.getResourceAsStream("/risk-coordinator.properties");
        final RiskCoordinatorConfig config = new RiskCoordinatorPropertiesLoader().load(inputStream);
        final RiskCoordinatorEngine engine = new RiskCoordinatorEngine(config.safeThreshold(), config.haltThreshold());
        final EventEmitter eventEmitter = new EventEmitter(eventsBusinessJournal, new RealTimeProvider(), config.nodeId());
        final RiskDecisionEmitter decisionEmitter = new RiskDecisionEmitter(eventEmitter, config.safeThreshold(), config.haltThreshold());
        final RiskCoordinatorTailer tailer = new RiskCoordinatorTailer(eventsBusinessJournal, marketDataJournal, config.receivedCapacity());
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "jwcore-risk-coordinator-tick"));
        final GracefulShutdownCoordinator coordinator = new GracefulShutdownCoordinator(Duration.ofSeconds(10), thread -> Runtime.getRuntime().addShutdownHook(thread));
        coordinator.register(new GracefulShutdownParticipant() {
            @Override public String name() { return "risk-coordinator-tailer"; }
            @Override public void flush() { tailer.close(); shutdownScheduler(scheduler); }
            @Override public void snapshot() { }
        });
        coordinator.install();
        return new Application(engine, decisionEmitter, tailer, config, scheduler, coordinator);
    }

    private static void shutdownScheduler(final ScheduledExecutorService scheduler) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (final InterruptedException exception) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public record Application(RiskCoordinatorEngine engine,
                              RiskDecisionEmitter decisionEmitter,
                              RiskCoordinatorTailer tailer,
                              RiskCoordinatorConfig config,
                              ScheduledExecutorService scheduler,
                              GracefulShutdownCoordinator coordinator) {
        public Application {
            Objects.requireNonNull(engine, "engine cannot be null");
            Objects.requireNonNull(decisionEmitter, "decisionEmitter cannot be null");
            Objects.requireNonNull(tailer, "tailer cannot be null");
            Objects.requireNonNull(config, "config cannot be null");
            Objects.requireNonNull(scheduler, "scheduler cannot be null");
            Objects.requireNonNull(coordinator, "coordinator cannot be null");
        }

        public void start() {
            tailer.start();
            scheduler.scheduleAtFixedRate(() -> {
                final Map<String, org.jwcore.execution.common.state.ExecutionState> current = engine.evaluate(tailer.received());
                decisionEmitter.setExposureByAccount(engine.latestExposureByAccount());
                decisionEmitter.emitChanges(current);
            }, 0L, config.tickIntervalMs(), TimeUnit.MILLISECONDS);
        }
    }
}
