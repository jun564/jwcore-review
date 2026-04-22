package org.jwcore.core.timeout;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.time.ITimeProvider;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class OrderTimeoutMonitor {
    private static final byte TIMEOUT_PAYLOAD_VERSION = 1;

    private final ITimeProvider timeProvider;
    private final IEventJournal eventJournal;
    private final Duration timeout;
    private final Map<String, PendingIntent> pendingByLocalIntentId = new HashMap<>();

    public OrderTimeoutMonitor(final ITimeProvider timeProvider,
                               final IEventJournal eventJournal,
                               final Duration timeout) {
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider cannot be null");
        this.eventJournal = Objects.requireNonNull(eventJournal, "eventJournal cannot be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout cannot be null");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public synchronized void registerPending(final EventEnvelope orderIntentEvent) {
        Objects.requireNonNull(orderIntentEvent, "orderIntentEvent cannot be null");
        if (orderIntentEvent.eventType() != EventType.OrderIntentEvent) {
            throw new IllegalArgumentException("Only OrderIntentEvent can be registered as pending");
        }
        if (orderIntentEvent.localIntentId() == null || orderIntentEvent.localIntentId().isBlank()) {
            throw new IllegalArgumentException("OrderIntentEvent must contain localIntentId");
        }
        final long deadline = timeProvider.monotonicTime() + timeout.toNanos();
        pendingByLocalIntentId.put(orderIntentEvent.localIntentId(), new PendingIntent(orderIntentEvent, deadline));
    }

    public synchronized boolean markTerminal(final String localIntentId) {
        Objects.requireNonNull(localIntentId, "localIntentId cannot be null");
        return pendingByLocalIntentId.remove(localIntentId) != null;
    }

    public List<EventEnvelope> scanTimeouts() {
        final List<EventEnvelope> timedOut;
        synchronized (this) {
            final long now = timeProvider.monotonicTime();
            timedOut = new ArrayList<>();
            pendingByLocalIntentId.entrySet().removeIf(entry -> {
                final PendingIntent pendingIntent = entry.getValue();
                if (now < pendingIntent.deadlineNanos()) {
                    return false;
                }
                timedOut.add(timeoutEventFor(pendingIntent.originalEvent()));
                return true;
            });
        }

        for (final EventEnvelope timeoutEvent : timedOut) {
            eventJournal.append(timeoutEvent);
        }
        return List.copyOf(timedOut);
    }

    public synchronized int pendingCount() {
        return pendingByLocalIntentId.size();
    }

    private EventEnvelope timeoutEventFor(final EventEnvelope originalEvent) {
        final byte[] payload = ("timeout:" + originalEvent.localIntentId()).getBytes(StandardCharsets.UTF_8);
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.OrderTimeoutEvent,
                originalEvent.brokerOrderId(),
                originalEvent.localIntentId(),
                originalEvent.canonicalId(),
                IdempotencyKeys.generate(originalEvent.brokerOrderId(), EventType.OrderTimeoutEvent, payload),
                timeProvider.monotonicTime(),
                timeProvider.eventTime(),
                TIMEOUT_PAYLOAD_VERSION,
                payload
        );
    }

    private record PendingIntent(EventEnvelope originalEvent, long deadlineNanos) {
    }
}
