package org.jwcore.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalIdTest {

    @Test
    void shouldParseValidCanonicalId() {
        CanonicalId canonicalId = CanonicalId.parse("S07:I03:VA07-03:BA01");
        assertEquals(7, canonicalId.strategyNumber());
        assertEquals(3, canonicalId.instanceNumber());
        assertEquals(1, canonicalId.brokerAccountNumber());
        assertEquals("S07:I03:VA07-03:BA01", canonicalId.format());
    }

    @Test
    void shouldRejectInvalidCanonicalIdFormat() {
        assertThrows(IllegalArgumentException.class, () -> CanonicalId.parse("invalid"));
    }

    @Test
    void shouldRejectVirtualAccountInstanceMismatch() {
        assertThrows(IllegalArgumentException.class, () -> new CanonicalId(7, 3, 7, 4, 1));
    }
}
