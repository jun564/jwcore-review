package org.jwcore.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderIntentTest {
    private static final CanonicalId ID = CanonicalId.parse("S07:I03:VA07-03:BA01");
    private static final Instrument INSTR = new Instrument("EUR/USD");

    @Test
    void shouldCreateValidOrderIntent() {
        assertDoesNotThrow(() -> new OrderIntent(ID, INSTR, 0.01));
    }

    @Test
    void shouldRejectNullCanonicalId() {
        assertThrows(NullPointerException.class, () -> new OrderIntent(null, INSTR, 0.01));
    }

    @Test
    void shouldRejectNullInstrument() {
        assertThrows(NullPointerException.class, () -> new OrderIntent(ID, null, 0.01));
    }

    @Test
    void shouldRejectNonPositiveVolume() {
        assertThrows(IllegalArgumentException.class, () -> new OrderIntent(ID, INSTR, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new OrderIntent(ID, INSTR, -1.0));
    }

    @Test
    void shouldRejectNonFiniteVolume() {
        assertThrows(IllegalArgumentException.class, () -> new OrderIntent(ID, INSTR, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new OrderIntent(ID, INSTR, Double.NEGATIVE_INFINITY));
    }
}
