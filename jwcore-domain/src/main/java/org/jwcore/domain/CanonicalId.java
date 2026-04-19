package org.jwcore.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical identifier for a strategy instance and its attached virtual/broker account.
 * Format: S07:I03:VA07-03:BA01
 */
public record CanonicalId(
        int strategyNumber,
        int instanceNumber,
        int virtualAccountStrategyNumber,
        int virtualAccountInstanceNumber,
        int brokerAccountNumber) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Pattern PATTERN = Pattern.compile(
            "^S(?<strategy>\\d{2}):I(?<instance>\\d{2}):VA(?<vaStrategy>\\d{2})-(?<vaInstance>\\d{2}):BA(?<broker>\\d{2})$");

    public CanonicalId {
        validateRange(strategyNumber, "strategyNumber");
        validateRange(instanceNumber, "instanceNumber");
        validateRange(virtualAccountStrategyNumber, "virtualAccountStrategyNumber");
        validateRange(virtualAccountInstanceNumber, "virtualAccountInstanceNumber");
        validateRange(brokerAccountNumber, "brokerAccountNumber");
        if (strategyNumber != virtualAccountStrategyNumber || instanceNumber != virtualAccountInstanceNumber) {
            throw new IllegalArgumentException("Virtual account part must match strategy and instance numbers.");
        }
    }

    public static CanonicalId parse(final String raw) {
        Objects.requireNonNull(raw, "raw canonical id cannot be null");
        final Matcher matcher = PATTERN.matcher(raw);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid CanonicalId format: " + raw);
        }

        return new CanonicalId(
                Integer.parseInt(matcher.group("strategy")),
                Integer.parseInt(matcher.group("instance")),
                Integer.parseInt(matcher.group("vaStrategy")),
                Integer.parseInt(matcher.group("vaInstance")),
                Integer.parseInt(matcher.group("broker"))
        );
    }

    public String format() {
        return "S%02d:I%02d:VA%02d-%02d:BA%02d".formatted(
                strategyNumber,
                instanceNumber,
                virtualAccountStrategyNumber,
                virtualAccountInstanceNumber,
                brokerAccountNumber
        );
    }

    @Override
    public String toString() {
        return format();
    }

    private static void validateRange(final int value, final String fieldName) {
        if (value < 0 || value > 99) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 99");
        }
    }
}
