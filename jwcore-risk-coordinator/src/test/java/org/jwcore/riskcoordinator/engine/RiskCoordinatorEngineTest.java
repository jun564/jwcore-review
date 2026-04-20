package org.jwcore.riskcoordinator.engine;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.IdempotencyKeys;
import org.jwcore.execution.common.state.ExecutionState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RiskCoordinatorEngineTest {
    @Test
    void shouldReturnRunWhenExposureBelowSafeThreshold() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));

        final var result = engine.evaluate(List.of(orderIntentEvent("crypto-account|BTCUSDT|99")));

        assertEquals(ExecutionState.RUN, result.get("crypto-account"));
    }

    @Test
    void shouldReturnSafeWhenExposureBetweenThresholds() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));

        final var result = engine.evaluate(List.of(orderIntentEvent("crypto-account|BTCUSDT|120")));

        assertEquals(ExecutionState.SAFE, result.get("crypto-account"));
    }

    @Test
    void shouldReturnHaltWhenExposureAboveHaltThreshold() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));

        final var result = engine.evaluate(List.of(orderIntentEvent("crypto-account|BTCUSDT|150")));

        assertEquals(ExecutionState.HALT, result.get("crypto-account"));
    }

    @Test
    void shouldAggregateExposurePerAccountId() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));

        final var result = engine.evaluate(List.of(
                orderIntentEvent("crypto-account|BTCUSDT|80"),
                orderIntentEvent("crypto-account|ETHUSD|30"),
                orderIntentEvent("forex-account|EURUSD|170")
        ));

        assertEquals(ExecutionState.SAFE, result.get("crypto-account"));
        assertEquals(ExecutionState.HALT, result.get("forex-account"));
    }

    @Test
    void shouldSkipEventWhenAccountIdMissing() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));

        final var result = engine.evaluate(List.of(orderIntentEvent("|BTCUSDT|130")));

        assertFalse(result.containsKey(""));
        assertEquals(0, result.size());
    }

    private static EventEnvelope orderIntentEvent(final String payloadText) {
        final byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.OrderIntentEvent,
                null,
                UUID.randomUUID().toString(),
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                IdempotencyKeys.generate(null, EventType.OrderIntentEvent, payload),
                1L,
                Instant.parse("2026-04-19T08:00:00Z"),
                (byte) 1,
                payload,
                "risk-coordinator-test",
                UUID.randomUUID()
        );
    }
}
