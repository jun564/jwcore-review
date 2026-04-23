package org.jwcore.adapter.jforex.session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class TestScheduler extends AbstractExecutorService implements ScheduledExecutorService {
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final List<Long> delaysSeconds = new ArrayList<>();
    private boolean shutdown;

    @Override
    public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
        delaysSeconds.add(unit.toSeconds(delay));
        tasks.add(command);
        return new CompletedScheduledFuture();
    }

    void runNext() {
        final Runnable runnable = tasks.poll();
        if (runnable != null) {
            runnable.run();
        }
    }

    void runAll() {
        while (!tasks.isEmpty()) {
            runNext();
        }
    }

    List<Long> delaysSeconds() {
        return delaysSeconds;
    }

    @Override public void shutdown() { shutdown = true; }
    @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
    @Override public boolean isShutdown() { return shutdown; }
    @Override public boolean isTerminated() { return shutdown; }
    @Override public boolean awaitTermination(final long timeout, final TimeUnit unit) { return true; }
    @Override public void execute(final Runnable command) { command.run(); }
    @Override public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) { throw new UnsupportedOperationException(); }
    @Override public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay, final long period, final TimeUnit unit) { throw new UnsupportedOperationException(); }
    @Override public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay, final long delay, final TimeUnit unit) { throw new UnsupportedOperationException(); }

    @Override public <T> Future<T> submit(final Callable<T> task) { throw new UnsupportedOperationException(); }
    @Override public <T> Future<T> submit(final Runnable task, final T result) { throw new UnsupportedOperationException(); }
    @Override public Future<?> submit(final Runnable task) { throw new UnsupportedOperationException(); }
    @Override public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) { throw new UnsupportedOperationException(); }
    @Override public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) { throw new UnsupportedOperationException(); }
    @Override public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) { throw new UnsupportedOperationException(); }
    @Override public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) { throw new UnsupportedOperationException(); }

    private static final class CompletedScheduledFuture implements ScheduledFuture<Object> {
        @Override public long getDelay(final TimeUnit unit) { return 0; }
        @Override public int compareTo(final Delayed o) { return 0; }
        @Override public boolean cancel(final boolean mayInterruptIfRunning) { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return true; }
        @Override public Object get() { return null; }
        @Override public Object get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { return null; }
    }
}
