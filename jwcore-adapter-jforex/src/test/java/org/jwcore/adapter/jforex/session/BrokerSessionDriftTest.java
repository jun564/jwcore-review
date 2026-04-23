package org.jwcore.adapter.jforex.session;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.events.DriftClassification;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrokerSessionDriftTest {
    private static final CanonicalId CID = CanonicalId.parse("S07:I03:VA07-03:BA01");

    @Test
    void shouldClassifyNoneWhenSnapshotMatches() {
        final MockBroker broker = new MockBroker();
        broker.setSnapshot(Map.of(CID, new ReconcileSnapshot(new BigDecimal("10"), new BigDecimal("10"), 2, 2)));
        assertEquals(DriftClassification.NONE, DriftDetector.classifyAll(broker.snapshot()));
    }

    @Test
    void shouldClassifyMinorWhenPendingDiffIsOneAndNetMatches() {
        final MockBroker broker = new MockBroker();
        broker.setSnapshot(Map.of(CID, new ReconcileSnapshot(new BigDecimal("10"), new BigDecimal("10"), 2, 3)));
        assertEquals(DriftClassification.MINOR, DriftDetector.classifyAll(broker.snapshot()));
    }

    @Test
    void shouldClassifyMajorWhenNetDiffersButSignMatches() {
        final MockBroker broker = new MockBroker();
        broker.setSnapshot(Map.of(CID, new ReconcileSnapshot(new BigDecimal("10"), new BigDecimal("10.5"), 2, 2)));
        assertEquals(DriftClassification.MAJOR, DriftDetector.classifyAll(broker.snapshot()));
    }

    @Test
    void shouldClassifyFatalWhenSignDiffers() {
        final MockBroker broker = new MockBroker();
        broker.setSnapshot(Map.of(CID, new ReconcileSnapshot(new BigDecimal("10"), new BigDecimal("-10"), 2, 2)));
        assertEquals(DriftClassification.FATAL, DriftDetector.classifyAll(broker.snapshot()));
    }

    @Test
    void shouldClassifyFatalWhenLedgerOpenButBrokerClosed() {
        final MockBroker broker = new MockBroker();
        broker.setSnapshot(Map.of(CID, new ReconcileSnapshot(new BigDecimal("10"), BigDecimal.ZERO, 2, 2)));
        assertEquals(DriftClassification.FATAL, DriftDetector.classifyAll(broker.snapshot()));
    }
}
