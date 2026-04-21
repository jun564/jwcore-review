package org.jwcore.riskcoordinator.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExposureLedgerTest {
    @Test
    void shouldAddExposure() {
        final ExposureLedger ledger = new ExposureLedger();

        ledger.add("crypto", new BigDecimal("100"));

        assertEquals(new BigDecimal("100"), ledger.exposureOf("crypto"));
    }

    @Test
    void shouldSubtractExposure() {
        final ExposureLedger ledger = new ExposureLedger();
        ledger.add("crypto", new BigDecimal("100"));

        final boolean clamped = ledger.subtract("crypto", new BigDecimal("30"));

        assertEquals(new BigDecimal("70"), ledger.exposureOf("crypto"));
        assertFalse(clamped);
    }

    @Test
    void shouldClampToZeroOnNegativeSubtract() {
        final ExposureLedger ledger = new ExposureLedger();
        ledger.add("crypto", new BigDecimal("10"));

        final boolean clamped = ledger.subtract("crypto", new BigDecimal("30"));

        assertTrue(clamped);
        assertEquals(BigDecimal.ZERO, ledger.exposureOf("crypto"));
    }

    @Test
    void shouldReturnZeroForUnknownAccount() {
        final ExposureLedger ledger = new ExposureLedger();

        assertEquals(BigDecimal.ZERO, ledger.exposureOf("missing"));
    }

    @Test
    void shouldNotMutateSnapshotAfterReturn() {
        final ExposureLedger ledger = new ExposureLedger();
        ledger.add("crypto", new BigDecimal("5"));

        final Map<String, BigDecimal> snapshot = ledger.snapshot();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("x", BigDecimal.ONE));
    }

    @Test
    void shouldRejectBlankAccountId() {
        final ExposureLedger ledger = new ExposureLedger();

        assertThrows(IllegalArgumentException.class, () -> ledger.add(" ", BigDecimal.ONE));
    }

    @Test
    void shouldRejectNonPositiveSize() {
        final ExposureLedger ledger = new ExposureLedger();

        assertThrows(IllegalArgumentException.class, () -> ledger.add("crypto", BigDecimal.ZERO));
    }
}
