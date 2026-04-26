package org.jwcore.execution.forex.app;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.shutdown.GracefulShutdownCoordinator;
import org.jwcore.core.shutdown.GracefulShutdownParticipant;
import org.jwcore.core.time.RealTimeProvider;
import org.jwcore.execution.common.state.ExecutionState;
import org.jwcore.execution.forex.app.support.InMemoryEventJournal;
import org.jwcore.execution.common.broker.StubBrokerSession;
import org.jwcore.execution.forex.config.ExecutionPropertiesLoader;
import org.jwcore.execution.forex.runtime.ExecutionRuntime;
import org.jwcore.execution.forex.runtime.ExecutionRuntimeConfig;

import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main {
    private Main() {}

    public static void main(final String[] args) throws Exception {
        final Application application = bootstrap(new InMemoryEventJournal());
        application.start();
    }

    public static Application bootstrap(final IEventJournal eventJournal) throws Exception {
        Objects.requireNonNull(eventJournal, "eventJournal cannot be null");
        final InputStream inputStream = Main.class.getResourceAsStream("/forex.properties");
        final ExecutionRuntimeConfig config = new ExecutionPropertiesLoader().load(inputStream, "forex");
        final ExecutionRuntime runtime = new ExecutionRuntime(config, eventJournal, new RealTimeProvider(), new StubBrokerSession("FOREX-ORD-"), snapshot -> ExecutionState.RUN);
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "jwcore-execution-forex-tick"));
        final GracefulShutdownCoordinator coordinator = new GracefulShutdownCoordinator(Duration.ofSeconds(10), thread -> Runtime.getRuntime().addShutdownHook(thread));
        coordinator.register(new GracefulShutdownParticipant() {
            @Override public String name() { return "forex-scheduler"; }
            @Override public void flush() { shutdownScheduler(scheduler); }
            @Override public void snapshot() { }
        });
        coordinator.install();
        return new Application(runtime, config, scheduler, coordinator);
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

    public record Application(ExecutionRuntime runtime,
                              ExecutionRuntimeConfig config,
                              ScheduledExecutorService scheduler,
                              GracefulShutdownCoordinator coordinator) {
        public Application {
            Objects.requireNonNull(runtime, "runtime cannot be null");
            Objects.requireNonNull(config, "config cannot be null");
            Objects.requireNonNull(scheduler, "scheduler cannot be null");
            Objects.requireNonNull(coordinator, "coordinator cannot be null");
        }

        public void start() {
            scheduler.scheduleAtFixedRate(runtime::tickCycle, 0L, config.tickIntervalMs(), TimeUnit.MILLISECONDS);
        }
    }
}
