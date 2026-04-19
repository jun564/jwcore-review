package org.jwcore.core.time;

import java.time.Instant;

public interface ITimeProvider {
    long monotonicTime();
    Instant eventTime();
}
