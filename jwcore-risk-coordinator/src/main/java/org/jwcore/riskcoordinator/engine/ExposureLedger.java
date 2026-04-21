package org.jwcore.riskcoordinator.engine;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ExposureLedger {
    private final AtomicReference<Map<String, BigDecimal>> exposureByAccount = new AtomicReference<>(Map.of());

    public void add(final String accountId, final BigDecimal size) {
        validateInput(accountId, size);
        final Map<String, BigDecimal> current = exposureByAccount.get();
        final Map<String, BigDecimal> updated = new HashMap<>(current);
        updated.merge(accountId, size, BigDecimal::add);
        exposureByAccount.set(updated);
    }

    public boolean subtract(final String accountId, final BigDecimal size) {
        validateInput(accountId, size);
        final Map<String, BigDecimal> current = exposureByAccount.get();
        final Map<String, BigDecimal> updated = new HashMap<>(current);
        final BigDecimal existing = current.getOrDefault(accountId, BigDecimal.ZERO);
        final BigDecimal candidate = existing.subtract(size);
        final boolean clamped = candidate.signum() < 0;
        updated.put(accountId, clamped ? BigDecimal.ZERO : candidate);
        exposureByAccount.set(updated);
        return clamped;
    }

    public BigDecimal exposureOf(final String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
        return exposureByAccount.get().getOrDefault(accountId, BigDecimal.ZERO);
    }

    public Map<String, BigDecimal> snapshot() {
        return Collections.unmodifiableMap(exposureByAccount.get());
    }

    private static void validateInput(final String accountId, final BigDecimal size) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
        Objects.requireNonNull(size, "size cannot be null");
        if (size.signum() <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
    }
}
