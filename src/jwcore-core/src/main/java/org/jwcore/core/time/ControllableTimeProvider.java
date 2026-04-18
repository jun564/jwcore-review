package org.jwcore.core.time;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ControllableTimeProvider implements ITimeProvider {

    private final AtomicLong monotonicTime;
    private final AtomicReference<Instant> eventTime;

    public ControllableTimeProvider(final long initialMonotonicTime, final Instant initialEventTime) {
        if (initialMonotonicTime < 0L) {
            throw new IllegalArgumentException("initialMonotonicTime cannot be negative");
        }
        this.monotonicTime = new AtomicLong(initialMonotonicTime);
        this.eventTime = new AtomicReference<>(Objects.requireNonNull(initialEventTime, "initialEventTime cannot be null"));
    }

    @Override
    public long monotonicTime() {
        return monotonicTime.get();
    }

    @Override
    public Instant eventTime() {
        return eventTime.get();
    }

    public void advanceBy(final Duration duration) {
        Objects.requireNonNull(duration, "duration cannot be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration cannot be negative");
        }
        monotonicTime.addAndGet(duration.toNanos());
        eventTime.updateAndGet(current -> current.plus(duration));
    }

    public void setMonotonicTime(final long newMonotonicTime) {
        if (newMonotonicTime < 0L) {
            throw new IllegalArgumentException("newMonotonicTime cannot be negative");
        }
        monotonicTime.set(newMonotonicTime);
    }

    public void setEventTime(final Instant newEventTime) {
        eventTime.set(Objects.requireNonNull(newEventTime, "newEventTime cannot be null"));
    }
}
