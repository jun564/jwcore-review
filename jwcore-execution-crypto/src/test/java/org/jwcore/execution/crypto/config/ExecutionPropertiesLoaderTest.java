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
        final String content = "account.id=crypto\nexecution.timeout.ms=15000\nbroker.timeout.ms=30000\nmargin.emit.every.cycles=4\ntick.interval.ms=200\nprocessed.events.capacity=123\nnodeId=crypto-execution-node-1\n";
        final ExecutionRuntimeConfig config = new ExecutionPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "default");
        assertEquals("crypto", config.accountId());
        assertEquals(Duration.ofMillis(15000), config.executionTimeout());
        assertEquals(Duration.ofMillis(30000), config.brokerTimeout());
        assertEquals(4, config.marginEmitEveryNCycles());
        assertEquals(200L, config.tickIntervalMs());
        assertEquals(123, config.processedEventsCapacity());
        assertEquals("crypto-execution-node-1", config.nodeId());
    }

    @Test
    void shouldRejectBrokerTimeoutLowerThanExecutionTimeout() {
        final String content = "execution.timeout.ms=2000\nbroker.timeout.ms=1000\nnodeId=test-node\n";
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "default"));
    }

    @Test
    void shouldRejectInvalidPositiveValues() {
        final String content = "tick.interval.ms=0\n";
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "default"));
    }

    @Test
    void shouldThrowWhenNodeIdIsBlank() {
        final String content = "nodeId=\n";
        assertThrows(IllegalStateException.class,
                () -> new ExecutionPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "default"));
    }

    @Test
    void shouldThrowWhenNodeIdIsWhitespace() {
        final String content = "nodeId=   \n";
        assertThrows(IllegalStateException.class,
                () -> new ExecutionPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "default"));
    }
}
