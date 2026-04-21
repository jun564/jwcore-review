package org.jwcore.riskcoordinator.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiskCoordinatorPropertiesLoaderTest {
    @Test
    void shouldLoadHappyPathProperties() throws Exception {
        final String content = "safe.threshold=100\nhalt.threshold=150\ntick.interval.ms=250\nnodeId=risk-coordinator-test\nrisk.monitored.accounts=crypto, forex\n";
        final RiskCoordinatorConfig config = new RiskCoordinatorPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        assertEquals("100", config.safeThreshold().toPlainString());
        assertEquals("150", config.haltThreshold().toPlainString());
        assertEquals(250L, config.tickIntervalMs());
        assertEquals("risk-coordinator-test", config.nodeId());
        assertEquals(List.of("crypto", "forex"), config.monitoredAccounts());
    }

    @Test
    void shouldRejectMissingMonitoredAccounts() {
        final String content = "safe.threshold=100\nhalt.threshold=150\ntick.interval.ms=250\nnodeId=risk-coordinator-test\n";
        assertThrows(IllegalStateException.class,
                () -> new RiskCoordinatorPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void shouldRejectBlankMonitoredAccounts() {
        final String content = "safe.threshold=100\nhalt.threshold=150\ntick.interval.ms=250\nnodeId=risk-coordinator-test\nrisk.monitored.accounts=   ,   \n";
        assertThrows(IllegalStateException.class,
                () -> new RiskCoordinatorPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void shouldRejectInvalidProperties() {
        final String content = "safe.threshold=-1\nnodeId=risk-coordinator-test\nrisk.monitored.accounts=crypto\n";
        assertThrows(IllegalArgumentException.class,
                () -> new RiskCoordinatorPropertiesLoader().load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));

        final String blankNodeId = "safe.threshold=100\nhalt.threshold=150\ntick.interval.ms=250\nnodeId=   \nrisk.monitored.accounts=crypto\n";
        assertThrows(IllegalArgumentException.class,
                () -> new RiskCoordinatorPropertiesLoader().load(new ByteArrayInputStream(blankNodeId.getBytes(StandardCharsets.UTF_8))));
    }
}
