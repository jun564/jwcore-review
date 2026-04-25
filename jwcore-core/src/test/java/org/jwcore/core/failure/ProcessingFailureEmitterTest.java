package org.jwcore.core.failure;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.core.time.ControllableTimeProvider;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.events.EventProcessingFailedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingFailureEmitterTest {

    @Test
    void shouldCreateFailureEnvelopeAndAppendToJournalWithV3Payload() {
        final RecordingJournal journal = new RecordingJournal();
        final ControllableTimeProvider timeProvider = new ControllableTimeProvider(42L, Instant.parse("2026-04-24T10:00:00Z"));
        final ProcessingFailureEmitter emitter = new ProcessingFailureEmitter(journal, timeProvider, "risk-node-1");
        final UUID failedEventId = UUID.randomUUID();

        final EventProcessingFailedEvent failure = emitter.emit(failedEventId, new IllegalStateException("boom"), 3,
                "risk-coordinator", "OrderFilledEvent", "ACC-1");

        assertEquals(1, journal.appendCalls);
        assertEquals(EventType.EventProcessingFailedEvent, failure.envelope().eventType());
        assertEquals("failed:" + failedEventId + ":3", failure.envelope().idempotencyKey());
        assertEquals(failure.envelope(), journal.appended.get(0));
        assertEquals(3, failure.attemptNumber());
        assertTrue(failure.isPermanent());

        final EventProcessingFailedEvent decoded = EventProcessingFailedEvent.fromPayload(
                failure.envelope().payload(), failure.envelope().payloadVersion());
        assertEquals("risk-coordinator", decoded.sourceModule());
        assertEquals("OrderFilledEvent", decoded.originalEventType());
        assertEquals("ACC-1", decoded.failedAccountId());
        assertEquals(failedEventId.toString(), failure.envelope().localIntentId());
        assertNull(failure.envelope().correlationId());
    }

    @Test
    void shouldSanitizeAndTruncateErrorMessage() {
        final RecordingJournal journal = new RecordingJournal();
        final ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-24T10:00:00Z"));
        final ProcessingFailureEmitter emitter = new ProcessingFailureEmitter(journal, timeProvider, "risk-node-1");
        final String rawMessage = "a|" + "b".repeat(600);

        final EventProcessingFailedEvent event = emitter.emit(UUID.randomUUID(), new IllegalArgumentException(rawMessage));

        assertEquals(512, event.errorMessage().length());
        assertEquals('/', event.errorMessage().charAt(1));
        assertEquals(1, event.attemptNumber());
        assertEquals("unknown", event.sourceModule());
    }

    @Test
    void shouldSupportLegacyEmitMethod() {
        final RecordingJournal journal = new RecordingJournal();
        final ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-24T10:00:00Z"));
        final ProcessingFailureEmitter emitter = new ProcessingFailureEmitter(journal, timeProvider, "risk-node-1");

        final EventProcessingFailedEvent event = emitter.emit(UUID.randomUUID(), new RuntimeException("boom"));

        assertEquals(1, event.attemptNumber());
        assertEquals("unknown", event.sourceModule());
        assertNotNull(event.envelope());
    }

    @Test
    void shouldValidateConstructorAndEmitArguments() {
        final ControllableTimeProvider timeProvider = new ControllableTimeProvider(0L, Instant.parse("2026-04-24T10:00:00Z"));
        final RecordingJournal journal = new RecordingJournal();

        assertThrows(NullPointerException.class, () -> new ProcessingFailureEmitter(null, timeProvider, "risk-node-1"));
        assertThrows(NullPointerException.class, () -> new ProcessingFailureEmitter(journal, null, "risk-node-1"));
        assertThrows(NullPointerException.class, () -> new ProcessingFailureEmitter(journal, timeProvider, null));
        assertThrows(IllegalArgumentException.class, () -> new ProcessingFailureEmitter(journal, timeProvider, "  "));

        final ProcessingFailureEmitter emitter = new ProcessingFailureEmitter(journal, timeProvider, "risk-node-1");
        assertThrows(NullPointerException.class, () -> emitter.emit(null, new RuntimeException("boom")));
        assertThrows(NullPointerException.class, () -> emitter.emit(UUID.randomUUID(), null));
    }

    private static final class RecordingJournal implements IEventJournal {
        private final List<EventEnvelope> appended = new ArrayList<>();
        private int appendCalls;

        @Override
        public long append(final EventEnvelope envelope) {
            appendCalls++;
            appended.add(envelope);
            return appendCalls;
        }

        @Override
        public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) {
            return List.of();
        }

        @Override
        public long currentSequence() {
            return appended.size();
        }

        @Override
        public List<EventEnvelope> readAfterSequence(final long sequence) {
            return List.of();
        }

        @Override
        public TailSubscription tail(final Consumer<EventEnvelope> consumer) {
            return () -> { };
        }
    }
}
