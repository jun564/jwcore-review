package org.jwcore.riskcoordinator.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiskCoordinatorPropertiesLoaderTest {
    @Test
    void shouldLoadHappyPathProperties() throws Exception {
        final String content = "safe.threshold=100\nhalt.threshold=150\ntick.interval.ms=250\nrisk.coordinator.received.capacity=500\n";
        final RiskCoordinatorConfig config = new RiskCoordinatorPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        assertEquals("100", config.safeThreshold().toPlainString());
        assertEquals("150", config.haltThreshold().toPlainString());
        assertEquals(250L, config.tickIntervalMs());
        assertEquals(500, config.receivedCapacity());
    }

    @Test
    void shouldUseDefaultReceivedCapacityWhenMissing() throws Exception {
        final String content = "safe.threshold=100\nhalt.threshold=150\ntick.interval.ms=250\n";
        final RiskCoordinatorConfig config = new RiskCoordinatorPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        assertEquals(10000, config.receivedCapacity());
    }

    @Test
    void shouldRejectNonPositiveReceivedCapacity() {
        final String zeroCapacity = "safe.threshold=100\nhalt.threshold=150\ntick.interval.ms=250\nrisk.coordinator.received.capacity=0\n";
        assertThrows(IllegalArgumentException.class,
                () -> new RiskCoordinatorPropertiesLoader().load(new ByteArrayInputStream(zeroCapacity.getBytes(StandardCharsets.UTF_8))));

        final String negativeCapacity = "safe.threshold=100\nhalt.threshold=150\ntick.interval.ms=250\nrisk.coordinator.received.capacity=-1\n";
        assertThrows(IllegalArgumentException.class,
                () -> new RiskCoordinatorPropertiesLoader().load(new ByteArrayInputStream(negativeCapacity.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void shouldRejectInvalidProperties() {
        final String content = "safe.threshold=-1\n";
        assertThrows(IllegalArgumentException.class,
                () -> new RiskCoordinatorPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
    }
}
