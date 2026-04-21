package org.jwcore.execution.common.runtime;

import org.jwcore.core.time.ITimeProvider;
import org.jwcore.execution.common.events.OrderTimeoutEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public final class OrderTimeoutTracker {
    private final ITimeProvider timeProvider;
    private final Map<UUID, PendingIntent> pendingByIntentId = new LinkedHashMap<>();

    public OrderTimeoutTracker(final ITimeProvider timeProvider) {
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider cannot be null");
    }

    public void register(final PendingIntent pendingIntent) {
        final PendingIntent safePendingIntent = Objects.requireNonNull(pendingIntent, "pendingIntent cannot be null");
        pendingByIntentId.put(safePendingIntent.intentId(), safePendingIntent);
    }

    public boolean remove(final UUID intentId) {
        return pendingByIntentId.remove(intentId) != null;
    }

    public int pendingCount() {
        return pendingByIntentId.size();
    }

    public void checkTimeouts(final Function<PendingIntent, OrderTimeoutEvent> eventFactory,
                              final Consumer<OrderTimeoutEvent> onTimeout) {
        Objects.requireNonNull(eventFactory, "eventFactory cannot be null");
        Objects.requireNonNull(onTimeout, "onTimeout cannot be null");

        final var now = timeProvider.eventTime();
        final List<PendingIntent> expired = new ArrayList<>();
        for (final PendingIntent pendingIntent : pendingByIntentId.values()) {
            if (!now.isBefore(pendingIntent.emittedAt().plus(Duration.ofMillis(pendingIntent.timeoutThresholdMs())))) {
                expired.add(pendingIntent);
            }
        }

        for (final PendingIntent pendingIntent : expired) {
            pendingByIntentId.remove(pendingIntent.intentId());
        }

        for (final PendingIntent pendingIntent : expired) {
            onTimeout.accept(eventFactory.apply(pendingIntent));
        }
    }
}
