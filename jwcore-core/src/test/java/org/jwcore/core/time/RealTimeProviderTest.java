package org.jwcore.core.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RealTimeProviderTest {

    @Test
    void monotonicTimeShouldIncrease() {
        RealTimeProvider provider = new RealTimeProvider();
        long t1 = provider.monotonicTime();
        long t2 = provider.monotonicTime();
        assertTrue(t2 >= t1);
    }

    @Test
    void eventTimeShouldNotBeNull() {
        RealTimeProvider provider = new RealTimeProvider();
        assertNotNull(provider.eventTime());
    }
}
