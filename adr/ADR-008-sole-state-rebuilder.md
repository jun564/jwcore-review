# ADR-008 — Execution jako jedyny rebuilder stanu

**Data:** 18.04.2026
**Status:** Zatwierdzony (Gemini + GPT + Claude + Architekt)
**Kontekst:** Event sourcing w JWCore zakłada że stan systemu odtwarzany jest z Chronicle Queue. Otwarte pytanie od sesji Claude 10: który komponent jest źródłem prawdy przy replay. Decyzja niezbędna przed Sprintem 3 (Execution Adapter).

## Decyzja

**Execution Adapter jest jedynym komponentem odtwarzającym stan z CQ.**

Inne komponenty (Strategy Host, Risk Engine, Risk Coordinator, GUI) **nie rekonstruują** własnego stanu pozycji z CQ samodzielnie — otrzymują aktualny stan przez eventy emitowane przez Execution.

## Granica odpowiedzialności (dopisek GPT)

**jwcore-risk-coordinator** jest **konsumentem** stanu, nie jego rebuilderem:
- Nie czyta CQ celem rekonstrukcji stanu pozycji
- Subskrybuje `StateRebuiltEvent` i eventy inkrementalne z obu procesów Execution
- Utrzymuje **własny stan ryzyka cross-account** (totalna ekspozycja, korelacja, limity portfolio), ale **nie duplikuje stanu pozycji**
- Rebuild stanu brokerowego pozostaje **wyłączną kompetencją** `jwcore-execution-crypto` i `jwcore-execution-forex`

## Mechanizm

### Start Execution Adapter

1. Czyta CQ od ostatniego snapshotu (jeśli istnieje) lub od początku kolejki
2. Rekonstruuje stan pozycji, orderów in-flight, idempotency keys z eventów
3. Wykonuje **reconciliation** z brokerem:
   - Query aktualnego stanu pozycji/orderów z Dukascopy
   - Porównanie z stanem odtworzonym z CQ
   - Wykrycie discrepancy
4. Emituje `StateRebuiltEvent` z aktualnym snapshotem

### Inne komponenty

1. Czekają na `StateRebuiltEvent` z odpowiednim `account_id` po starcie
2. Od tego momentu przetwarzają eventy inkrementalne na bieżąco
3. Jeśli same restartują — czekają na kolejny `StateRebuiltEvent` lub wysyłają `StateQueryCommand`

## Struktura StateRebuiltEvent (wg wymagań GPT #3)

```java
StateRebuiltEvent {
    String account_id;              // "crypto" | "forex"
    int snapshot_version;            // wersja formatu snapshotu
    UUID rebuilt_until_event_id;     // ostatni event włączony do rebuild
    Instant rebuilt_until_timestamp; // timestamp eventu
    RebuildType type;                // CLEAN | AFTER_DISCREPANCY | AFTER_RECONCILIATION
    int events_replayed;             // liczba eventów przetworzonych
    List<Discrepancy> discrepancies; // lista wykrytych rozbieżności (pusta dla CLEAN)
    EventEnvelope envelope;          // standardowy envelope
}

enum RebuildType {
    CLEAN,                  // brak rozbieżności z brokerem
    AFTER_DISCREPANCY,      // wykryto rozbieżności, rebuild wymusił korektę
    AFTER_RECONCILIATION    // rebuild po reconciliation z OrderTimeoutEvent
}

record Discrepancy(
    String description,
    String expected_state,
    String actual_state,
    Instant detected_at
) {}
```

## Brak split-brain

- Tylko Execution może stwierdzić "pozycja X istnieje" (ma bezpośrednie połączenie z brokerem)
- Inne komponenty tylko wierzą temu co Execution ogłasza przez eventy
- Jeśli Execution padnie i restartuje — emituje nowy `StateRebuiltEvent` i świat synchronizuje się z tym
- Risk Coordinator nigdy nie twierdzi "Execution się myli o stanie pozycji"

## Dwa rachunki — konsekwencja

Każdy Execution (crypto, forex) jest rebuilderem **własnego** fragmentu stanu. Emituje `StateRebuiltEvent` z własnym `account_id`. Risk Coordinator zbiera oba i łączy w pełny obraz ryzyka (nie pełny obraz stanu pozycji — tylko zagregowane ryzyko).

## Konsekwencje

**Pozytywne:**
- Jedna prawda o stanie pozycji — brak rozbieżności
- Prostsze recovery innych komponentów
- Event sourcing zachowuje integrację z rzeczywistością broker-side
- Brak wyścigów przy równoczesnym starcie wielu komponentów

**Negatywne:**
- Czas startu systemu zależy od czasu rebuilda Execution
- Wszystkie komponenty muszą czekać na `StateRebuiltEvent` przed rozpoczęciem pracy

**Mitygacja:**
- Startup snapshotu + delta replay (nie pełny replay od początku czasu) — szybkość startu
- Każdy Execution rebuilduje niezależnie — nie blokują się wzajemnie
- Risk Coordinator może już konsumować market-data podczas gdy czeka na state rebuilt

## Zależności

- ADR-003 (Event Sourcing)
- ADR-006 (Event Envelope)
- ADR-011 (dwa procesy execution, osobny risk coordinator)
