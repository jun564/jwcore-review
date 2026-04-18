package org.jwcore.core.backpressure;

public final class BackpressureController {
    private final BackpressureThresholds thresholds;

    public BackpressureController(final BackpressureThresholds thresholds) {
        this.thresholds = thresholds;
    }

    public BackpressureDecision evaluate(final BackpressureSnapshot snapshot) {
        final boolean optionalLagged = snapshot.tailers().stream()
                .anyMatch(t -> t.role() == TailerRole.OPTIONAL && t.lagMessages() >= thresholds.optionalTailerLagMessages());
        final boolean criticalLagged = snapshot.tailers().stream()
                .anyMatch(t -> t.role() == TailerRole.CRITICAL && t.lagMessages() >= thresholds.criticalTailerLagMessages());

        if (snapshot.ramUtilization() >= thresholds.haltRamUtilization() && criticalLagged) {
            return new BackpressureDecision(BackpressureLevel.HALT, "Critical tailer lag with high RAM pressure");
        }
        if (snapshot.ramUtilization() >= thresholds.throttleRamUtilization() || criticalLagged) {
            return new BackpressureDecision(BackpressureLevel.THROTTLE_ORDER_INTENT, "Throttle order intents due to pressure or critical lag");
        }
        if (snapshot.ramUtilization() >= thresholds.alertRamUtilization() || optionalLagged) {
            return new BackpressureDecision(BackpressureLevel.ALERT, "Alert due to early pressure or optional tailer lag");
        }
        return new BackpressureDecision(BackpressureLevel.OK, "System healthy");
    }
}
