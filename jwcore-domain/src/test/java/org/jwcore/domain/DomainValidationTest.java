package org.jwcore.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DomainValidationTest {

    @Test
    void shouldRejectBlankSymbol() {
        assertThrows(IllegalArgumentException.class, () -> new Instrument("   "));
        assertThrows(IllegalArgumentException.class, () -> new Instrument(""));
    }

    @Test
    void shouldCreateValidTick() {
        assertDoesNotThrow(() -> new Tick(new Instrument("BTC/USD"), 100.0, 101.0, Instant.now()));
    }

    @Test
    void shouldRejectNullTickFields() {
        Instant t = Instant.now();
        assertThrows(NullPointerException.class, () -> new Tick(null, 100.0, 101.0, t));
        assertThrows(NullPointerException.class, () -> new Tick(new Instrument("BTC/USD"), 100.0, 101.0, null));
    }

    @Test
    void shouldRejectNonFiniteOrNonPositiveTickPrices() {
        Instrument instrument = new Instrument("BTC/USD");
        Instant timestamp = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> new Tick(instrument, Double.NaN, 101.0, timestamp));
        assertThrows(IllegalArgumentException.class, () -> new Tick(instrument, 100.0, Double.POSITIVE_INFINITY, timestamp));
        assertThrows(IllegalArgumentException.class, () -> new Tick(instrument, 0.0, 101.0, timestamp));
        assertThrows(IllegalArgumentException.class, () -> new Tick(instrument, 100.0, 0.0, timestamp));
        assertThrows(IllegalArgumentException.class, () -> new Tick(instrument, 102.0, 101.0, timestamp));
    }

    @Test
    void shouldCreateValidBar() {
        assertDoesNotThrow(() -> new Bar(new Instrument("BTC/USD"), Timeframe.M1, 100.0, 110.0, 95.0, 105.0, 1.0, Instant.now()));
    }

    @Test
    void shouldRejectNonFiniteBarValues() {
        Instrument i = new Instrument("BTC/USD");
        Instant t = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> new Bar(i, Timeframe.M1, Double.NaN, 110.0, 95.0, 105.0, 1.0, t));
        assertThrows(IllegalArgumentException.class, () -> new Bar(i, Timeframe.M1, 100.0, Double.POSITIVE_INFINITY, 95.0, 105.0, 1.0, t));
    }

    @Test
    void shouldRejectNonPositiveOhlc() {
        Instrument i = new Instrument("BTC/USD");
        Instant t = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> new Bar(i, Timeframe.M1, 0.0, 110.0, 95.0, 105.0, 1.0, t));
        assertThrows(IllegalArgumentException.class, () -> new Bar(i, Timeframe.M1, 100.0, 110.0, 0.0, 105.0, 1.0, t));
    }

    @Test
    void shouldRejectHighLowerThanLow() {
        Instrument i = new Instrument("BTC/USD");
        assertThrows(IllegalArgumentException.class, () -> new Bar(i, Timeframe.M1, 100.0, 90.0, 95.0, 88.0, 1.0, Instant.now()));
    }

    @Test
    void shouldRejectLowHigherThanOpenOrClose() {
        Instrument i = new Instrument("BTC/USD");
        Instant t = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> new Bar(i, Timeframe.M1, 95.0, 110.0, 97.0, 105.0, 1.0, t));
        assertThrows(IllegalArgumentException.class, () -> new Bar(i, Timeframe.M1, 100.0, 110.0, 106.0, 105.0, 1.0, t));
    }

    @Test
    void shouldRejectNegativeVolume() {
        Instrument i = new Instrument("BTC/USD");
        assertThrows(IllegalArgumentException.class, () -> new Bar(i, Timeframe.M1, 100.0, 110.0, 95.0, 105.0, -0.1, Instant.now()));
    }

    @Test
    void shouldRejectNullBarFields() {
        Instant t = Instant.now();
        Instrument i = new Instrument("BTC/USD");
        assertThrows(NullPointerException.class, () -> new Bar(null, Timeframe.M1, 100.0, 110.0, 95.0, 105.0, 1.0, t));
        assertThrows(NullPointerException.class, () -> new Bar(i, null, 100.0, 110.0, 95.0, 105.0, 1.0, t));
        assertThrows(NullPointerException.class, () -> new Bar(i, Timeframe.M1, 100.0, 110.0, 95.0, 105.0, 1.0, null));
    }

    @Test
    void shouldExposeEventTypeValues() {
        // 1. Wszystkie wymagane wartości muszą istnieć
        java.util.Set<String> required = java.util.Set.of(
            "OrderIntentEvent", "OrderRejectedEvent", "OrderTimeoutEvent",
            "MarketDataEvent", "RiskDecisionEvent", "MarginUpdateEvent",
            "StateRebuiltEvent", "OrderFilledEvent", "OrderCanceledEvent",
            "OrderUnknownEvent", "ExecutionEvent", "ParameterUpdatedEvent",
            "EventProcessingFailedEvent"
        );
        java.util.Set<String> actual = java.util.Arrays.stream(EventType.values())
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(actual.containsAll(required),
            "Brakujące wartości EventType: " + required.stream()
                .filter(r -> !actual.contains(r))
                .collect(java.util.stream.Collectors.joining(", ")));

        // 2. Brak duplikatów nazw
        assertEquals(EventType.values().length, actual.size(),
            "EventType zawiera zduplikowane nazwy");

        // 3. Brak null w values()
        for (EventType et : EventType.values()) {
            assertNotNull(et, "EventType.values() zawiera null");
        }
    }

    @Test
    void shouldExposeTimeframeValues() {
        assertEquals(Timeframe.TICK, Timeframe.valueOf("TICK"));
        assertTrue(Timeframe.values().length >= 6);
    }

    @Test
    void shouldNormalizeInstrumentSymbolToUpperCase() {
        assertEquals("EUR/USD", new Instrument(" eur/usd ").symbol());
    }

}
