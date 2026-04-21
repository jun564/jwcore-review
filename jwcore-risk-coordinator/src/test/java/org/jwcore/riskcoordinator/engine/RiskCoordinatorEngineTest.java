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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RiskCoordinatorEngineTest {
    @Test
    void shouldReturnRunWhenExposureBelowSafeThreshold() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));

        final var result = engine.evaluate(List.of(orderSubmittedEvent("crypto-account|"
                + UUID.randomUUID() + "|BROKER-1|S07:I03:VA07-03:BA01|99|2026-04-20T10:00:00Z")));

        assertEquals(ExecutionState.RUN, result.get("crypto-account"));
    }

    @Test
    void shouldReturnSafeWhenExposureBetweenThresholds() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));

        final var result = engine.evaluate(List.of(orderSubmittedEvent(
                "crypto-account|" + UUID.randomUUID() + "|BROKER-1|S07:I03:VA07-03:BA01|120|2026-04-20T10:00:00Z")));

        assertEquals(ExecutionState.SAFE, result.get("crypto-account"));
    }

    @Test
    void shouldReturnHaltWhenExposureAboveHaltThreshold() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));

        final var result = engine.evaluate(List.of(orderSubmittedEvent(
                "crypto-account|" + UUID.randomUUID() + "|BROKER-1|S07:I03:VA07-03:BA01|151|2026-04-20T10:00:00Z")));

        assertEquals(ExecutionState.HALT, result.get("crypto-account"));
    }

    @Test
    void shouldAggregateExposurePerAccountId() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));

        final var result = engine.evaluate(List.of(
                orderSubmittedEvent("crypto-account|" + UUID.randomUUID() + "|BROKER-1|S07:I03:VA07-03:BA01|80|2026-04-20T10:00:00Z"),
                orderSubmittedEvent("crypto-account|" + UUID.randomUUID() + "|BROKER-2|S07:I04:VA07-04:BA01|30|2026-04-20T10:00:01Z"),
                orderSubmittedEvent("forex-account|" + UUID.randomUUID() + "|BROKER-3|S07:I05:VA07-05:BA01|170|2026-04-20T10:00:02Z")
        ));

        assertEquals(ExecutionState.SAFE, result.get("crypto-account"));
        assertEquals(ExecutionState.HALT, result.get("forex-account"));
    }

    @Test
    void shouldSkipEventWhenAccountIdMissing() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));

        final var result = engine.evaluate(List.of(orderSubmittedEvent("|"
                + UUID.randomUUID() + "|BROKER-1|S07:I03:VA07-03:BA01|130|2026-04-20T10:00:00Z")));

        assertFalse(result.containsKey(""));
        assertEquals(0, result.size());
    }

    @Test
    void shouldAggregateExposureAsSize() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));

        final var result = engine.evaluate(List.of(
                orderSubmittedEvent("crypto-account|" + UUID.randomUUID() + "|BROKER-1|S07:I03:VA07-03:BA01|40.5|2026-04-20T10:00:00Z"),
                orderSubmittedEvent("crypto-account|" + UUID.randomUUID() + "|BROKER-2|S07:I04:VA07-04:BA01|59.5|2026-04-20T10:00:01Z"),
                orderSubmittedEvent("crypto-account|" + UUID.randomUUID() + "|BROKER-3|S07:I05:VA07-05:BA01|10|2026-04-20T10:00:02Z")
        ));

        assertEquals(ExecutionState.SAFE, result.get("crypto-account"));
    }

    private static EventEnvelope orderSubmittedEvent(final String payloadText) {
        final byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
        final UUID intentId = UUID.randomUUID();
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.OrderSubmittedEvent,
                null,
                intentId.toString(),
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                IdempotencyKeys.generate(null, EventType.OrderSubmittedEvent, payload),
                1L,
                Instant.parse("2026-04-19T08:00:00Z"),
                (byte) 1,
                payload,
                "risk-coordinator-test",
                intentId
        );
    }

    @Test
    void shouldReturnEmptyMapForEmptyInput() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));
        assertEquals(0, engine.evaluate(List.of()).size());
    }

    @Test
    void shouldIgnoreNonOrderSubmittedEvents() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));
        final byte[] payload = "crypto-account|intentId|BROKER-1|S07:I03:VA07-03:BA01|99|2026-04-20T10:00:00Z"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final UUID intentId = UUID.randomUUID();
        final var intentEvent = new EventEnvelope(
                UUID.randomUUID(), EventType.OrderIntentEvent, null, intentId.toString(),
                CanonicalId.parse("S07:I03:VA07-03:BA01"),
                IdempotencyKeys.generate(null, EventType.OrderIntentEvent, payload),
                1L, Instant.parse("2026-04-19T08:00:00Z"), (byte) 1, payload,
                "risk-coordinator-test", intentId);
        assertEquals(0, engine.evaluate(List.of(intentEvent)).size());
    }

    @Test
    void shouldSkipEventWhenSizeIsNotParseable() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));
        final var result = engine.evaluate(List.of(orderSubmittedEvent(
                "crypto-account|" + UUID.randomUUID() + "|BROKER-1|S07:I03:VA07-03:BA01|NOT_A_NUMBER|2026-04-20T10:00:00Z")));
        assertEquals(0, result.size());
    }

    @Test
    void shouldEvaluateLegacyOverloadReturnRunState() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));
        final RiskAssessment assessment = engine.evaluate(new BigDecimal("40"), new BigDecimal("30"));
        assertEquals(ExecutionState.RUN, assessment.desiredState());
        assertEquals(new BigDecimal("70"), assessment.totalExposure());
    }

    @Test
    void shouldEvaluateLegacyOverloadReturnHaltState() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));
        final RiskAssessment assessment = engine.evaluate(new BigDecimal("100"), new BigDecimal("60"));
        assertEquals(ExecutionState.HALT, assessment.desiredState());
    }

    @Test
    void shouldReturnLatestExposureByAccount() {
        final var engine = new RiskCoordinatorEngine(new BigDecimal("100"), new BigDecimal("150"));
        engine.evaluate(List.of(
                orderSubmittedEvent("acct-A|" + UUID.randomUUID() + "|BROKER-1|S07:I03:VA07-03:BA01|80|2026-04-20T10:00:00Z")));
        final var latest = engine.latestExposureByAccount();
        assertEquals(new BigDecimal("80"), latest.get("acct-A"));
    }

}
