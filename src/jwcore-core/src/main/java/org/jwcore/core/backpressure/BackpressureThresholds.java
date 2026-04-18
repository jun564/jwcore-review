package org.jwcore.core.backpressure;

public record BackpressureThresholds(
        double alertRamUtilization,
        double throttleRamUtilization,
        double haltRamUtilization,
        long optionalTailerLagMessages,
        long criticalTailerLagMessages) {

    public BackpressureThresholds {
        validateRatio(alertRamUtilization, "alertRamUtilization");
        validateRatio(throttleRamUtilization, "throttleRamUtilization");
        validateRatio(haltRamUtilization, "haltRamUtilization");
        if (!(alertRamUtilization <= throttleRamUtilization && throttleRamUtilization <= haltRamUtilization)) {
            throw new IllegalArgumentException("RAM thresholds must be monotonic");
        }
        if (optionalTailerLagMessages < 0L || criticalTailerLagMessages < 0L) {
            throw new IllegalArgumentException("tailer lag thresholds cannot be negative");
        }
    }

    private static void validateRatio(final double ratio, final String fieldName) {
        if (!Double.isFinite(ratio) || ratio < 0.0 || ratio > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}
