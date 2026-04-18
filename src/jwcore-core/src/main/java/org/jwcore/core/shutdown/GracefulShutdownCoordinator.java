package org.jwcore.core.shutdown;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

public final class GracefulShutdownCoordinator {
    private final Duration timeout;
    private final ShutdownHookRegistrar registrar;
    private final List<GracefulShutdownParticipant> participants = new CopyOnWriteArrayList<>();
    private volatile boolean installed;

    public GracefulShutdownCoordinator(final Duration timeout, final ShutdownHookRegistrar registrar) {
        Objects.requireNonNull(timeout, "timeout cannot be null");
        Objects.requireNonNull(registrar, "registrar cannot be null");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
        this.registrar = registrar;
    }

    public void register(final GracefulShutdownParticipant participant) {
        participants.add(Objects.requireNonNull(participant, "participant cannot be null"));
    }

    public synchronized void install() {
        if (installed) {
            return;
        }
        registrar.register(new Thread(this::execute, "jwcore-graceful-shutdown"));
        installed = true;
    }

    public GracefulShutdownResult execute() {
        final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            final Thread thread = new Thread(r, "jwcore-shutdown-worker");
            thread.setDaemon(true);
            return thread;
        });
        final List<String> order = new ArrayList<>();
        try {
            final Future<?> future = executor.submit(() -> {
                for (final GracefulShutdownParticipant participant : participants) {
                    order.add(participant.name());
                    try {
                        participant.flush();
                        participant.snapshot();
                    } catch (final Exception exception) {
                        throw new CompletionException(exception);
                    }
                }
            });
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return new GracefulShutdownResult(true, false, List.copyOf(order));
        } catch (final TimeoutException exception) {
            return new GracefulShutdownResult(false, true, List.copyOf(order));
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new GracefulShutdownResult(false, false, List.copyOf(order));
        } catch (final ExecutionException exception) {
            return new GracefulShutdownResult(false, false, List.copyOf(order));
        } finally {
            executor.shutdownNow();
        }
    }
}
