package org.jwcore.adapter.jforex.session;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.events.DriftClassification;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public final class DriftDetector {
    private static final int SCALE = 8;

    private DriftDetector() {
    }

    public static DriftClassification classifyAll(final Map<CanonicalId, ReconcileSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots cannot be null");
        DriftClassification worst = DriftClassification.NONE;
        for (final ReconcileSnapshot snapshot : snapshots.values()) {
            final DriftClassification current = classifyOne(snapshot);
            if (current.ordinal() > worst.ordinal()) {
                worst = current;
            }
        }
        return worst;
    }

    public static DriftClassification classifyOne(final ReconcileSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        final BigDecimal ledger = snapshot.ledgerNetPosition();
        final BigDecimal broker = snapshot.brokerNetPosition();

        if (isFatalPositionDrift(ledger, broker)) {
            return DriftClassification.FATAL;
        }

        if (isMajorPositionDrift(ledger, broker)) {
            return DriftClassification.MAJOR;
        }

        final int pendingDiff = Math.abs(snapshot.ledgerIntentCount() - snapshot.brokerPendingCount());
        if (pendingDiff == 0) {
            return DriftClassification.NONE;
        }
        if (pendingDiff <= 1) {
            return DriftClassification.MINOR;
        }
        return DriftClassification.MAJOR;
    }

    private static boolean isFatalPositionDrift(final BigDecimal ledger, final BigDecimal broker) {
        final boolean ledgerOpen = ledger != null && ledger.signum() != 0;
        final boolean brokerOpen = broker != null && broker.signum() != 0;
        if (ledgerOpen != brokerOpen) {
            return true;
        }
        if (!ledgerOpen) {
            return false;
        }
        return ledger.signum() != broker.signum();
    }

    private static boolean isMajorPositionDrift(final BigDecimal ledger, final BigDecimal broker) {
        final BigDecimal safeLedger = ledger == null ? BigDecimal.ZERO : ledger;
        final BigDecimal safeBroker = broker == null ? BigDecimal.ZERO : broker;
        final BigDecimal delta = safeLedger.subtract(safeBroker).abs();
        if (delta.signum() == 0) {
            return false;
        }
        return safeLedger.setScale(SCALE, java.math.RoundingMode.HALF_UP)
                .compareTo(safeBroker.setScale(SCALE, java.math.RoundingMode.HALF_UP)) != 0;
    }
}
