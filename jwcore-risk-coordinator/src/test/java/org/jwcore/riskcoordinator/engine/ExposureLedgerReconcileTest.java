package org.jwcore.riskcoordinator.engine;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.jwcore.domain.OrderSide;
import org.jwcore.domain.events.BrokerReconcileEvent;
import org.jwcore.domain.events.DriftClassification;
import org.jwcore.domain.events.OrderFilledEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExposureLedgerReconcileTest {
    private static final CanonicalId CID = CanonicalId.parse("S07:I03:VA07-03:BA01");

    @Test
    void applyNoneShouldNotChangeLedgerState() {
        final ExposureLedger ledger = preparedLedger();
        final BigDecimal position = ledger.netPosition(CID);
        final BigDecimal pnl = ledger.realizedPnL(CID);

        ledger.apply(reconcile(DriftClassification.NONE));

        assertEquals(position, ledger.netPosition(CID));
        assertEquals(pnl, ledger.realizedPnL(CID));
    }

    @Test
    void applyMinorMajorFatalShouldNotChangeFinancialState() {
        final ExposureLedger ledger = preparedLedger();
        final BigDecimal position = ledger.netPosition(CID);
        final BigDecimal avg = ledger.averageEntryPrice(CID);
        final BigDecimal pnl = ledger.realizedPnL(CID);
        final int intents = ledger.intentCount(CID);

        ledger.apply(reconcile(DriftClassification.MINOR));
        ledger.apply(reconcile(DriftClassification.MAJOR));
        ledger.apply(reconcile(DriftClassification.FATAL));

        assertEquals(position, ledger.netPosition(CID));
        assertEquals(avg, ledger.averageEntryPrice(CID));
        assertEquals(pnl, ledger.realizedPnL(CID));
        assertEquals(intents, ledger.intentCount(CID));
    }

    @Test
    void reconcileAfterFillsShouldNotOverrideRealizedPnl() {
        final ExposureLedger ledger = preparedLedger();
        final BigDecimal pnlBefore = ledger.realizedPnL(CID);
        ledger.apply(reconcile(DriftClassification.MAJOR));
        assertEquals(pnlBefore, ledger.realizedPnL(CID));
    }

    private static ExposureLedger preparedLedger() {
        final ExposureLedger ledger = new ExposureLedger();
        ledger.apply(intent("o1"));
        ledger.apply(fill("o1", OrderSide.BUY, "5", "100", "1"));
        ledger.apply(intent("o2"));
        ledger.apply(fill("o2", OrderSide.SELL, "2", "120", "1"));
        return ledger;
    }

    private static EventEnvelope reconcile(final DriftClassification classification) {
        final BrokerReconcileEvent event = new BrokerReconcileEvent(CID, classification,
                new BigDecimal("3"), new BigDecimal("4"), 1, 2, ts(), 2, null);
        return envelope(EventType.BrokerReconcileEvent, event.toPayload(), "reconcile");
    }

    private static EventEnvelope intent(final String intentId) {
        return envelope(EventType.OrderIntentEvent, new byte[]{1}, intentId);
    }

    private static EventEnvelope fill(final String orderId, final OrderSide side, final String qty, final String price, final String commission) {
        final OrderFilledEvent event = new OrderFilledEvent(orderId, null, CID, side,
                new BigDecimal(qty), new BigDecimal(price), new BigDecimal(commission), ts(), BigDecimal.ZERO, null);
        return envelope(EventType.OrderFilledEvent, event.toPayload(), orderId);
    }

    private static EventEnvelope envelope(final EventType type, final byte[] payload, final String localIntentId) {
        return new EventEnvelope(UUID.randomUUID(), type, null, localIntentId, CID,
                IdempotencyKeys.generate(null, type, payload), 1L, ts(), (byte) 1, payload,
                "ledger-reconcile-test", UUID.randomUUID());
    }

    private static Instant ts() {
        return Instant.parse("2026-04-23T10:00:00Z");
    }
}
