package org.jwcore.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionTest {

    private static final CanonicalId ID = CanonicalId.parse("S07:I03:VA07-03:BA01");
    private static final Instrument INSTR = new Instrument("BTC/USD");

    @Test
    void shouldCreateValidPosition() {
        assertDoesNotThrow(() -> new Position("BRK-1", ID, INSTR, 0.1, 45000.0));
    }

    @Test
    void shouldRejectNullBrokerOrderId() {
        assertThrows(NullPointerException.class, () -> new Position(null, ID, INSTR, 0.1, 45000.0));
    }

    @Test
    void shouldRejectNullCanonicalId() {
        assertThrows(NullPointerException.class, () -> new Position("BRK-1", null, INSTR, 0.1, 45000.0));
    }

    @Test
    void shouldRejectNullInstrument() {
        assertThrows(NullPointerException.class, () -> new Position("BRK-1", ID, null, 0.1, 45000.0));
    }

    @Test
    void shouldRejectNonPositiveVolume() {
        assertThrows(IllegalArgumentException.class, () -> new Position("BRK-1", ID, INSTR, 0.0, 45000.0));
        assertThrows(IllegalArgumentException.class, () -> new Position("BRK-1", ID, INSTR, -1.0, 45000.0));
    }

    @Test
    void shouldRejectNonFiniteVolume() {
        assertThrows(IllegalArgumentException.class, () -> new Position("BRK-1", ID, INSTR, Double.NaN, 45000.0));
        assertThrows(IllegalArgumentException.class, () -> new Position("BRK-1", ID, INSTR, Double.POSITIVE_INFINITY, 45000.0));
    }

    @Test
    void shouldRejectNonPositiveAveragePrice() {
        assertThrows(IllegalArgumentException.class, () -> new Position("BRK-1", ID, INSTR, 0.1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Position("BRK-1", ID, INSTR, 0.1, -100.0));
    }

    @Test
    void shouldRejectNonFiniteAveragePrice() {
        assertThrows(IllegalArgumentException.class, () -> new Position("BRK-1", ID, INSTR, 0.1, Double.NaN));
    }
}
