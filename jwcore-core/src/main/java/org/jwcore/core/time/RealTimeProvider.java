package org.jwcore.core.time;

import java.time.Instant;

public final class RealTimeProvider implements ITimeProvider {
    @Override
    public long monotonicTime() {
        return System.nanoTime();
    }

    @Override
    public Instant eventTime() {
        return Instant.now();
    }
}
