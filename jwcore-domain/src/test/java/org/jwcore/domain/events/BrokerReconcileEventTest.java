package org.jwcore.domain.events;

import org.jwcore.domain.CanonicalId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrokerReconcileEventTest {

    @Test
    void shouldValidateConstructor() {
        final CanonicalId canonicalId = CanonicalId.parse("S07:I03:VA07-03:BA01");
        final Instant now = Instant.parse("2026-04-23T10:00:00Z");

        assertThrows(NullPointerException.class, () -> new BrokerReconcileEvent(
                null, DriftClassification.NONE, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, now, 0, null));
        assertThrows(NullPointerException.class, () -> new BrokerReconcileEvent(
                canonicalId, null, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, now, 0, null));
        assertThrows(NullPointerException.class, () -> new BrokerReconcileEvent(
                canonicalId, DriftClassification.NONE, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, null, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new BrokerReconcileEvent(
                canonicalId, DriftClassification.NONE, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, now, -1, null));
    }

    @Test
    void shouldSerializeDeserializeRoundTrip() {
        final BrokerReconcileEvent event = new BrokerReconcileEvent(
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                DriftClassification.MAJOR,
                new BigDecimal("1.10000000"),
                new BigDecimal("1.20000000"),
                2,
                1,
                Instant.parse("2026-04-23T10:00:00Z"),
                3,
                null);

        final BrokerReconcileEvent decoded = BrokerReconcileEvent.fromPayload(event.toPayload());
        assertEquals(event.canonicalId(), decoded.canonicalId());
        assertEquals(event.classification(), decoded.classification());
        assertEquals(event.localNetPosition(), decoded.localNetPosition());
        assertEquals(event.brokerNetPosition(), decoded.brokerNetPosition());
        assertEquals(event.localIntentCount(), decoded.localIntentCount());
        assertEquals(event.brokerPendingCount(), decoded.brokerPendingCount());
        assertEquals(event.timestampReconciled(), decoded.timestampReconciled());
        assertEquals(event.reconnectCount(), decoded.reconnectCount());
    }

    @Test
    void shouldHaveValueSemantics() {
        final BrokerReconcileEvent left = new BrokerReconcileEvent(
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                DriftClassification.MINOR,
                BigDecimal.ONE,
                BigDecimal.ONE,
                1,
                2,
                Instant.parse("2026-04-23T10:00:00Z"),
                1,
                null);
        final BrokerReconcileEvent right = new BrokerReconcileEvent(
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                DriftClassification.MINOR,
                BigDecimal.ONE,
                BigDecimal.ONE,
                1,
                2,
                Instant.parse("2026-04-23T10:00:00Z"),
                1,
                null);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }
}
