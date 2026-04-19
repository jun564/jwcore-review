package org.jwcore.riskcoordinator.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiskCoordinatorPropertiesLoaderTest {
    @Test
    void shouldLoadHappyPathProperties() throws Exception {
        final String content = "safe.threshold=100\nhalt.threshold=150\ntick.interval.ms=250\n";
        final RiskCoordinatorConfig config = new RiskCoordinatorPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        assertEquals("100", config.safeThreshold().toPlainString());
        assertEquals("150", config.haltThreshold().toPlainString());
        assertEquals(250L, config.tickIntervalMs());
    }

    @Test
    void shouldRejectInvalidProperties() {
        final String content = "safe.threshold=-1\n";
        assertThrows(IllegalArgumentException.class,
                () -> new RiskCoordinatorPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
    }
}
