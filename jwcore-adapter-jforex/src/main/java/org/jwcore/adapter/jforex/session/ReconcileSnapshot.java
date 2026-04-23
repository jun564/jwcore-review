package org.jwcore.adapter.jforex.session;

import java.math.BigDecimal;

public record ReconcileSnapshot(
        BigDecimal ledgerNetPosition,
        BigDecimal brokerNetPosition,
        int ledgerIntentCount,
        int brokerPendingCount) {
}
