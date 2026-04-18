package org.jwcore.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyKeysTest {

    @Test
    void shouldGenerateKeyWithNullBrokerIdAndNullPayload() {
        String key = IdempotencyKeys.generate(null, EventType.OrderIntentEvent, null);
        assertNotNull(key);
        assertFalse(key.isEmpty());
    }

    @Test
    void shouldGenerateKeyWithNonNullBrokerIdAndPayload() {
        String key = IdempotencyKeys.generate("BRK-1", EventType.ExecutionEvent, new byte[]{1, 2, 3});
        assertNotNull(key);
        assertFalse(key.isEmpty());
    }

    @Test
    void shouldProduceDifferentKeysForDifferentInputs() {
        String key1 = IdempotencyKeys.generate(null, EventType.OrderIntentEvent, null);
        String key2 = IdempotencyKeys.generate("BRK-1", EventType.OrderIntentEvent, null);
        assertNotEquals(key1, key2);
    }
}
