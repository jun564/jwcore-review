# ADR-009 — OrderTimeoutEvent

**Data:** 18.04.2026
**Status:** Zatwierdzony (Gemini + GPT + Claude + Architekt)
**Kontekst:** Po wysłaniu `OrderIntent` system oczekuje terminalnego eventu od brokera (`OrderFilledEvent`, `OrderRejectedEvent`, `OrderCanceledEvent`). W rzeczywistości pakiety mogą zaginąć, broker może nie odpowiedzieć, sieć może się zerwać między wysłaniem a potwierdzeniem.

## Decyzja

**Mechanizm timeoutu:** Jeśli przez określony czas po `OrderIntent` nie pojawi się żaden event terminalny dla danego `intent_id`, system emituje `OrderTimeoutEvent`.

## Parametry

- **Timeout domyślny:** 30 sekund
- **Konfigurowalność:** per rachunek (execution-crypto.properties, execution-forex.properties)
- **Timer:** `ControllableTimeProvider` (zgodne z ADR-001)
- **Źródło timerów:** proces Execution (on wysłał intent, on śledzi)

## Struktura eventu (wg wymagań GPT #6)

```java
public final class OrderTimeoutEvent {
    UUID intent_id;                  // identyfikator OrderIntent
    CanonicalId canonical_id;        // Sxx:Iyy:VAzz-nn:BAmm
    String account_id;               // "crypto" | "forex"
    long timeout_threshold_ms;       // wartość timeout użyta (z konfiguracji per-rachunek)
    Instant intent_emitted_at;       // kiedy wysłano OrderIntent
    Instant timeout_triggered_at;    // kiedy zarejestrowano timeout
    EventEnvelope envelope;          // standardowy envelope
}
```

## Reakcja na OrderTimeoutEvent

**Nie jest automatyczną decyzją biznesową.** Jest sygnałem dla Reconciliation Engine:

1. Reconciliation Engine (wbudowany w Execution) odpytuje brokera o status zlecenia
2. Jeśli broker zna:
   - Zlecenie zrealizowane → emisja właściwego `OrderFilledEvent` z opóźnieniem
   - Zlecenie anulowane → emisja `OrderCanceledEvent`
   - Zlecenie odrzucone → emisja `OrderRejectedEvent`
3. Jeśli broker nie zna — emisja `OrderUnknownEvent` i eskalacja do Architekta (Telegram notification)

**Strategia nie reaguje automatycznie na timeout** — zapobiega to double-send oraz kaskadowym decyzjom na podstawie niekompletnych informacji.

## Dlaczego per-rachunek konfigurowalny timeout

- **Krypto (BTC/ETH)** — szybki rynek, broker zazwyczaj odpowiada w <5s, timeout 30s może być za długi. Proponowany: 15s.
- **Forex główne pary** — stabilne, 30s OK
- **Forex egzotyki / niska płynność** — wolniejsze, proponowany 60s
- **Surowce** — zależne od sesji, domyślnie 30s

Wartość finalna ustalana empirycznie po Sprincie 3.2 (pierwsze testy na DEMO).

## Konsekwencje

**Pozytywne:**
- System nie wisi w oczekiwaniu na potwierdzenie
- Jawny event w CQ — audytowalne
- Reconciliation Engine ma punkt zaczepienia
- Zapobiega double-send

**Negatywne:**
- Timeout za długi = opóźniona reakcja na zagubione zlecenie
- Timeout za krótki = fałszywe timeouty przy normalnym opóźnieniu brokera
- Per-rachunek konfiguracja = więcej parametrów do nastrojenia

**Mitygacja:**
- Empiryczna kalibracja na DEMO przed live
- Telemetria: histogram czasów odpowiedzi brokera per rachunek

## Zależności

- ADR-001 (Time — ControllableTimeProvider)
- ADR-006 (Event Envelope)
- ADR-008 (Reconciliation w Execution)
- Sprint 3.2 — pełna implementacja timeoutów z JForex
