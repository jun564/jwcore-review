package org.jwcore.core.backpressure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackpressureControllerTest {

    private final BackpressureController controller = new BackpressureController(
            new BackpressureThresholds(0.60, 0.80, 0.95, 1_000, 500)
    );

    @Test
    void shouldStayOkWhenHealthy() {
        BackpressureDecision decision = controller.evaluate(new BackpressureSnapshot(0.20, List.of()));
        assertEquals(BackpressureLevel.OK, decision.level());
    }

    @Test
    void shouldAlertForOptionalTailerLag() {
        BackpressureDecision decision = controller.evaluate(new BackpressureSnapshot(0.20, List.of(
                new TailerLag("archiver", TailerRole.OPTIONAL, 1_500)
        )));
        assertEquals(BackpressureLevel.ALERT, decision.level());
    }

    @Test
    void shouldThrottleForCriticalTailerLag() {
        BackpressureDecision decision = controller.evaluate(new BackpressureSnapshot(0.50, List.of(
                new TailerLag("business-tail", TailerRole.CRITICAL, 800)
        )));
        assertEquals(BackpressureLevel.THROTTLE_ORDER_INTENT, decision.level());
    }

    @Test
    void shouldHaltForCriticalLagWithHighRamPressure() {
        BackpressureDecision decision = controller.evaluate(new BackpressureSnapshot(0.97, List.of(
                new TailerLag("business-tail", TailerRole.CRITICAL, 800)
        )));
        assertEquals(BackpressureLevel.HALT, decision.level());
    }

    @Test
    void shouldAlertForRamPressureWithoutTailerLag() {
        BackpressureDecision decision = controller.evaluate(new BackpressureSnapshot(0.65, List.of()));
        assertEquals(BackpressureLevel.ALERT, decision.level());
    }

    @Test
    void shouldThrottleForRamPressureWithoutCriticalLag() {
        BackpressureDecision decision = controller.evaluate(new BackpressureSnapshot(0.85, List.of()));
        assertEquals(BackpressureLevel.THROTTLE_ORDER_INTENT, decision.level());
    }

}
