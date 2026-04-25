package org.jwcore.domain.events;

import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventProcessingFailedEventTest {

    @Test
    void shouldRoundtripV3Payload() {
        final EventProcessingFailedEvent event = new EventProcessingFailedEvent(
                UUID.randomUUID(),
                IllegalStateException.class.getName(),
                "boom",
                Instant.parse("2026-04-25T11:00:00Z"),
                3,
                true,
                "risk-coordinator",
                "OrderFilledEvent",
                "ACC-1",
                envelope((byte) 3, new byte[0])
        );

        final EventProcessingFailedEvent parsed = EventProcessingFailedEvent.fromPayload(event.toPayload(), (byte) 3);

        assertEquals(event.failedEventId(), parsed.failedEventId());
        assertEquals(event.errorType(), parsed.errorType());
        assertEquals(event.errorMessage(), parsed.errorMessage());
        assertEquals(event.timestamp(), parsed.timestamp());
        assertEquals(event.attemptNumber(), parsed.attemptNumber());
        assertTrue(parsed.isPermanent());
        assertEquals("risk-coordinator", parsed.sourceModule());
        assertEquals("OrderFilledEvent", parsed.originalEventType());
        assertEquals("ACC-1", parsed.failedAccountId());
    }

    @Test
    void shouldParseLegacyV2PayloadWithDefaults() {
        final UUID failedEventId = UUID.randomUUID();
        final byte[] legacy = String.join("|", failedEventId.toString(), "java.lang.IllegalArgumentException", "bad", "2026-04-24T10:00:00Z")
                .getBytes(StandardCharsets.UTF_8);

        final EventProcessingFailedEvent parsed = EventProcessingFailedEvent.fromPayload(legacy, (byte) 2);

        assertEquals(failedEventId, parsed.failedEventId());
        assertEquals(1, parsed.attemptNumber());
        assertFalse(parsed.isPermanent());
        assertEquals("unknown", parsed.sourceModule());
        assertNull(parsed.originalEventType());
        assertNull(parsed.failedAccountId());
    }

    @Test
    void shouldValidateAttemptAndPermanentCoherence() {
        assertThrows(IllegalArgumentException.class, () -> new EventProcessingFailedEvent(
                UUID.randomUUID(), "t", "m", Instant.now(), 0, false, "risk", null, null, envelope((byte) 3, new byte[0])));

        assertThrows(IllegalArgumentException.class, () -> new EventProcessingFailedEvent(
                UUID.randomUUID(), "t", "m", Instant.now(), 2, true, "risk", null, null, envelope((byte) 3, new byte[0])));
    }

    private static EventEnvelope envelope(final byte version, final byte[] payload) {
        return new EventEnvelope(UUID.randomUUID(), EventType.EventProcessingFailedEvent, null, null, null,
                "k", 0L, Instant.now(), version, payload, "test", null);
    }
}
