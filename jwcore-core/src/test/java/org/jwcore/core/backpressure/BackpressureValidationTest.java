package org.jwcore.core.backpressure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackpressureValidationTest {

    @Test
    void shouldRejectInvalidThresholdRatios() {
        assertThrows(IllegalArgumentException.class, () -> new BackpressureThresholds(-0.1, 0.8, 0.9, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BackpressureThresholds(0.7, 0.6, 0.9, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BackpressureThresholds(0.6, 0.8, 1.1, 1, 1));
    }

    @Test
    void shouldRejectNegativeLagThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new BackpressureThresholds(0.6, 0.8, 0.9, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BackpressureThresholds(0.6, 0.8, 0.9, 1, -1));
    }

    @Test
    void shouldRejectInvalidSnapshotAndTailerLag() {
        assertThrows(IllegalArgumentException.class, () -> new BackpressureSnapshot(-0.1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BackpressureSnapshot(1.1, List.of()));
        assertThrows(NullPointerException.class, () -> new BackpressureSnapshot(0.5, null));
        assertThrows(NullPointerException.class, () -> new TailerLag(null, TailerRole.CRITICAL, 1));
        assertThrows(NullPointerException.class, () -> new TailerLag("x", null, 1));
        assertThrows(IllegalArgumentException.class, () -> new TailerLag("x", TailerRole.CRITICAL, -1));
    }

    @Test
    void shouldDefensivelyCopyTailers() {
        var original = new java.util.ArrayList<TailerLag>();
        original.add(new TailerLag("archiver", TailerRole.OPTIONAL, 1));
        BackpressureSnapshot snapshot = new BackpressureSnapshot(0.2, original);
        original.clear();
        assertEquals(1, snapshot.tailers().size());
    }
}
