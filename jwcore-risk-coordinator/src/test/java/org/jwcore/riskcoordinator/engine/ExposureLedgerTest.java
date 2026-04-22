package org.jwcore.riskcoordinator.engine;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.jwcore.domain.OrderSide;
import org.jwcore.domain.RejectReason;
import org.jwcore.domain.events.OrderCanceledEvent;
import org.jwcore.domain.events.OrderFilledEvent;
import org.jwcore.domain.events.OrderRejectedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExposureLedgerTest {
    private static final CanonicalId CID = CanonicalId.parse("S07:I03:VA07-03:BA01");

    @Test
    void shouldApplyLongShortAndPartialFills() {
        final ExposureLedger ledger = new ExposureLedger();
        ledger.apply(intent(CID, "o1"));
        ledger.apply(fill("o1", OrderSide.BUY, "10", "100", "0.1"));
        assertEquals(new BigDecimal("10"), ledger.netPosition(CID));

        ledger.apply(intent(CID, "o2"));
        ledger.apply(fill("o2", OrderSide.SELL, "3", "110", "0.1"));
        assertEquals(new BigDecimal("7"), ledger.netPosition(CID));

        ledger.apply(intent(CID, "o3"));
        ledger.apply(fill("o3", OrderSide.SELL, "10", "90", "0.1"));
        assertEquals(new BigDecimal("-3"), ledger.netPosition(CID));
    }

    @Test
    void shouldCalculateVwapAndKeepOnPartialClose() {
        final ExposureLedger ledger = new ExposureLedger();
        ledger.apply(intent(CID, "o1"));
        ledger.apply(fill("o1", OrderSide.BUY, "2", "100", "0"));
        ledger.apply(intent(CID, "o2"));
        ledger.apply(fill("o2", OrderSide.BUY, "3", "110", "0"));
        assertEquals(new BigDecimal("106.00000000"), ledger.averageEntryPrice(CID));

        ledger.apply(intent(CID, "o3"));
        ledger.apply(fill("o3", OrderSide.SELL, "1", "120", "0"));
        assertEquals(new BigDecimal("106.00000000"), ledger.averageEntryPrice(CID));
    }

    @Test
    void shouldCalculateRealizedPnlNetCommissionForCloseAndReverse() {
        final ExposureLedger ledger = new ExposureLedger();
        ledger.apply(intent(CID, "o1"));
        ledger.apply(fill("o1", OrderSide.BUY, "5", "100", "1"));
        ledger.apply(intent(CID, "o2"));
        ledger.apply(fill("o2", OrderSide.SELL, "2", "110", "1"));
        assertEquals(new BigDecimal("18"), ledger.realizedPnL(CID));

        ledger.apply(intent(CID, "o3"));
        ledger.apply(fill("o3", OrderSide.SELL, "10", "90", "1"));
        assertEquals(new BigDecimal("-12"), ledger.realizedPnL(CID));
        assertEquals(new BigDecimal("10"), ledger.totalCommission(CID));
    }

    @Test
    void shouldResetAveragePriceToZeroAfterFullClose() {
        final ExposureLedger ledger = new ExposureLedger();
        ledger.apply(intent(CID, "o1"));
        ledger.apply(fill("o1", OrderSide.BUY, "2", "100", "0"));
        ledger.apply(intent(CID, "o2"));
        ledger.apply(fill("o2", OrderSide.SELL, "2", "105", "0"));

        assertEquals(BigDecimal.ZERO, ledger.netPosition(CID));
        assertEquals(BigDecimal.ZERO, ledger.averageEntryPrice(CID));
    }

    @Test
    void shouldAggregateExposureMarginAndCommission() {
        final ExposureLedger ledger = new ExposureLedger();
        ledger.apply(intent(CID, "o1"));
        ledger.apply(fill("o1", OrderSide.BUY, "2", "100", "0.5"));

        final CanonicalId c2 = CanonicalId.parse("S07:I04:VA07-04:BA01");
        ledger.apply(intent(c2, "o2"));
        ledger.apply(fill(c2, "o2", OrderSide.SELL, "3", "50", "0.2"));

        assertEquals(new BigDecimal("350"), ledger.totalExposure());
        assertEquals(new BigDecimal("3.50"), ledger.marginUsed());
        assertEquals(new BigDecimal("0.5"), ledger.totalCommission(CID));
        assertEquals(new BigDecimal("0.2"), ledger.totalCommission(c2));
    }

    @Test
    void shouldTrackIntentCountAndFailFastForMissingPending() {
        final ExposureLedger ledger = new ExposureLedger();
        ledger.apply(intent(CID, "o1"));
        ledger.apply(rejected("o1", CID));
        assertEquals(0, ledger.intentCount(CID));

        ledger.apply(intent(CID, "o2"));
        ledger.apply(canceled("o2", CID));
        assertEquals(0, ledger.intentCount(CID));

        ledger.apply(intent(CID, "o3"));
        ledger.apply(fill("o3", OrderSide.BUY, "1", "10", "0"));
        assertEquals(0, ledger.intentCount(CID));

        assertThrows(IllegalStateException.class, () -> ledger.apply(canceled("x", CID)));
        assertThrows(IllegalStateException.class, () -> ledger.apply(rejected("x", CID)));
        assertThrows(IllegalStateException.class, () -> ledger.apply(fill("x", OrderSide.BUY, "1", "10", "0")));
    }

    @Test
    void shouldReturnZeroAverageEntryPriceForUnknownCanonicalId() {
        final ExposureLedger ledger = new ExposureLedger();
        assertEquals(BigDecimal.ZERO, ledger.averageEntryPrice(CID));
    }

    @Test
    void shouldBeDeterministicOnRebuild() {
        final ExposureLedger a = new ExposureLedger();
        final ExposureLedger b = new ExposureLedger();
        final EventEnvelope[] events = new EventEnvelope[]{
                intent(CID, "o1"), fill("o1", OrderSide.BUY, "2", "100", "1"),
                intent(CID, "o2"), fill("o2", OrderSide.SELL, "1", "110", "1")
        };
        for (EventEnvelope e : events) {
            a.apply(e);
            b.apply(e);
        }
        assertEquals(a.netPosition(CID), b.netPosition(CID));
        assertEquals(a.averageEntryPrice(CID), b.averageEntryPrice(CID));
        assertEquals(a.realizedPnL(CID), b.realizedPnL(CID));
        assertEquals(a.totalCommission(CID), b.totalCommission(CID));
        assertEquals(a.totalExposure(), b.totalExposure());
        assertEquals(a.marginUsed(), b.marginUsed());
    }

    private static EventEnvelope intent(final CanonicalId canonicalId, final String id) {
        return envelope(EventType.OrderIntentEvent, canonicalId, id, "i".getBytes());
    }

    private static EventEnvelope rejected(final String orderId, final CanonicalId canonicalId) {
        final OrderRejectedEvent event = new OrderRejectedEvent("acc", orderId, RejectReason.RISK_LIMIT, ts(), null);
        return envelope(EventType.OrderRejectedEvent, canonicalId, orderId, event.toPayload());
    }

    private static EventEnvelope canceled(final String orderId, final CanonicalId canonicalId) {
        return envelope(EventType.OrderCanceledEvent, canonicalId, orderId,
                new OrderCanceledEvent(orderId, null, canonicalId, "TIMEOUT", ts(), null).toPayload());
    }

    private static EventEnvelope fill(final String orderId, final OrderSide side, final String qty, final String price, final String commission) {
        return fill(CID, orderId, side, qty, price, commission);
    }

    private static EventEnvelope fill(final CanonicalId canonicalId, final String orderId, final OrderSide side,
                                      final String qty, final String price, final String commission) {
        return envelope(EventType.OrderFilledEvent, canonicalId, orderId,
                new OrderFilledEvent(orderId, null, canonicalId, side,
                        new BigDecimal(qty), new BigDecimal(price), new BigDecimal(commission), ts(), BigDecimal.ZERO, null).toPayload());
    }

    private static EventEnvelope envelope(final EventType type, final CanonicalId canonicalId, final String intentId, final byte[] payload) {
        return new EventEnvelope(UUID.randomUUID(), type, null, intentId, canonicalId,
                IdempotencyKeys.generate(null, type, payload), 1L, ts(), (byte) 1, payload,
                "ledger-test", UUID.randomUUID());
    }

    private static Instant ts() {
        return Instant.parse("2026-04-21T10:00:00Z");
    }
}
