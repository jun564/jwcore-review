package org.jwcore.execution.crypto.config;

import org.jwcore.execution.crypto.runtime.ExecutionRuntimeConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionPropertiesLoaderTest {
    @Test
    void shouldLoadHappyPathProperties() throws Exception {
        final String content = "account.id=crypto\norder.timeout.ms=15000\nmargin.emit.every.cycles=4\ntick.interval.ms=200\nprocessed.events.capacity=123\n";
        final ExecutionRuntimeConfig config = new ExecutionPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "default");
        assertEquals("crypto", config.accountId());
        assertEquals(Duration.ofMillis(15000), config.orderTimeout());
        assertEquals(4, config.marginEmitEveryNCycles());
        assertEquals(200L, config.tickIntervalMs());
        assertEquals(123, config.processedEventsCapacity());
    }

    @Test
    void shouldRejectInvalidPositiveValues() {
        final String content = "tick.interval.ms=0\n";
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "default"));
    }
}
