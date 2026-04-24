package org.jwcore.adapter.alerting;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.events.AlertEvent;
import org.jwcore.domain.events.AlertSeverity;
import org.jwcore.domain.events.AlertType;
import org.jwcore.domain.ExecutionState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertTailerTest {

    @Test
    void shouldDispatchToMultipleSinksAndIsolateFailures() {
        final IEventJournal journal = mock(IEventJournal.class);
        final TailSubscription subscription = mock(TailSubscription.class);
        final ArgumentCaptor<Consumer<EventEnvelope>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(journal.tail(captor.capture())).thenReturn(subscription);

        final List<AlertEvent> delivered = new ArrayList<>();
        final AlertSink failing = new AlertSink() {
            @Override public void deliver(final AlertEvent alert) { throw new RuntimeException("boom"); }
            @Override public String name() { return "fail"; }
        };
        final AlertSink ok = new AlertSink() {
            @Override public void deliver(final AlertEvent alert) { delivered.add(alert); }
            @Override public String name() { return "ok"; }
        };

        final AlertTailer tailer = new AlertTailer(journal, List.of(failing, ok));
        tailer.start();
        captor.getValue().accept(alertEnvelope());

        assertEquals(1, delivered.size());

        tailer.stop();
        verify(subscription).close();
    }

    @Test
    void shouldIgnoreNonAlertEvents() {
        final IEventJournal journal = mock(IEventJournal.class);
        final TailSubscription subscription = mock(TailSubscription.class);
        final AtomicBoolean called = new AtomicBoolean(false);
        doAnswer(invocation -> {
            Consumer<EventEnvelope> c = invocation.getArgument(0);
            c.accept(new EventEnvelope(UUID.randomUUID(), EventType.OrderIntentEvent, null, null, null, "k", 1,
                    Instant.now(), (byte) 1, new byte[]{1}, "src", null));
            return subscription;
        }).when(journal).tail(org.mockito.ArgumentMatchers.any());

        final AlertSink sink = new AlertSink() {
            @Override public void deliver(final AlertEvent alert) { called.set(true); }
            @Override public String name() { return "s"; }
        };

        final AlertTailer tailer = new AlertTailer(journal, List.of(sink));
        tailer.start();
        assertTrue(!called.get());
    }

    private static EventEnvelope alertEnvelope() {
        final AlertEvent alert = new AlertEvent(UUID.randomUUID(), "acc", AlertSeverity.WARNING,
                ExecutionState.RUN, ExecutionState.SAFE, AlertType.STATE_TRANSITION,
                "state-transition", List.of(), Instant.parse("2026-04-24T12:00:00Z"));
        return new EventEnvelope(alert.alertId(), EventType.AlertEvent, null, null, null, "k", 1,
                alert.occurredAt(), (byte) 1, alert.toPayload(), "src", null);
    }
}
