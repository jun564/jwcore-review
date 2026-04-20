package org.jwcore.domain.events;

import org.jwcore.domain.Instrument;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderIntentEventTest {
    @Test
    void shouldCreatePayloadWithAccountIdAsFirstField() {
        final OrderIntentEvent event = new OrderIntentEvent("crypto-account", new Instrument("BTCUSDT"), 0.10);

        assertEquals("crypto-account|BTCUSDT|0.1", new String(event.toPayload(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldRejectNullAccountId() {
        assertThrows(NullPointerException.class, () -> new OrderIntentEvent(null, new Instrument("BTCUSDT"), 0.10));
    }

    @Test
    void shouldRejectBlankAccountId() {
        assertThrows(IllegalArgumentException.class, () -> new OrderIntentEvent("   ", new Instrument("BTCUSDT"), 0.10));
    }

    @Test
    void shouldRoundTripPayload() {
        final byte[] payload = "forex-account|EURUSD|0.2".getBytes(StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> {
            final OrderIntentEvent parsed = OrderIntentEvent.fromPayload(payload);
            assertEquals("forex-account", parsed.accountId());
            assertEquals("EURUSD", parsed.instrument().value());
            assertEquals(0.2d, parsed.size());
        });
    }
}
