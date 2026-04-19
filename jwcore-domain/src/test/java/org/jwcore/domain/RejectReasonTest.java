package org.jwcore.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RejectReasonTest {

    @Test
    void shouldContainAllRequiredRejectReasons() {
        EnumSet<RejectReason> reasons = EnumSet.allOf(RejectReason.class);

        assertEquals(3, reasons.size());
        assertTrue(reasons.contains(RejectReason.SAFE_STATE));
        assertTrue(reasons.contains(RejectReason.HALT_STATE));
        assertTrue(reasons.contains(RejectReason.RISK_LIMIT));
    }
}
