package org.jwcore.execution.common.broker;

import org.jwcore.core.ports.IEventJournal;
import org.jwcore.core.ports.TailSubscription;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JournalBrokerEventListenerTest {

    @Test
    void shouldAppendEventToJournal() {
        final var journal = new RecordingJournal();
        final var listener = new JournalBrokerEventListener(journal);
        final var envelope = testEnvelope();

        listener.onBrokerEvent(envelope);

        assertEquals(1, journal.appendCalls);
        assertSame(envelope, journal.lastEnvelope);
    }

    @Test
    void shouldThrowOnNullJournalInConstructor() {
        assertThrows(NullPointerException.class, () -> new JournalBrokerEventListener(null));
    }

    @Test
    void shouldThrowOnNullEnvelope() {
        final var listener = new JournalBrokerEventListener(new RecordingJournal());

        assertThrows(NullPointerException.class, () -> listener.onBrokerEvent(null));
    }

    @Test
    void shouldPropagateJournalException() {
        final RuntimeException expected = new RuntimeException("append failed");
        final var listener = new JournalBrokerEventListener(new ThrowingJournal(expected));

        final RuntimeException thrown = assertThrows(RuntimeException.class, () -> listener.onBrokerEvent(testEnvelope()));
        assertSame(expected, thrown);
    }

    private static EventEnvelope testEnvelope() {
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.MarginUpdateEvent,
                null,
                null,
                null,
                "idempotency:test",
                0L,
                Instant.parse("2026-04-19T08:00:00Z"),
                (byte) 1,
                new byte[] {1},
                "test-source",
                UUID.randomUUID()
        );
    }

    private static final class RecordingJournal implements IEventJournal {
        private int appendCalls;
        private EventEnvelope lastEnvelope;

        @Override
        public long append(final EventEnvelope envelope) {
            appendCalls++;
            lastEnvelope = envelope;
            return appendCalls;
        }

        @Override
        public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) {
            return List.of();
        }

        @Override
        public long currentSequence() {
            return appendCalls;
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

    private record ThrowingJournal(RuntimeException failure) implements IEventJournal {

        @Override
        public long append(final EventEnvelope envelope) {
            throw failure;
        }

        @Override
        public List<EventEnvelope> read(final Instant fromInclusive, final Instant toExclusive) {
            return List.of();
        }

        @Override
        public long currentSequence() {
            return 0;
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
