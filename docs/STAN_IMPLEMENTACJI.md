# Stan implementacji JWCore

**Wersja:** 1.1 (22.04.2026 wieczór)
**Branch:** main
**Ostatni commit merge:** 818f1f3 (PR #24, Paczka 4C2/C). HEAD main: 311bc32 (docs sesji 25.04).

---

## Moduły i status kompilacji

| Moduł | Status | Testów | Uwagi |
|-------|--------|--------|-------|
| jwcore-parent | SUCCESS | — | — |
| jwcore-domain | SUCCESS | 75 | Nowe: OrderSide, OrderFilledEvent v2, OrderCanceledEvent v2 |
| jwcore-core | SUCCESS | 30 | OrderTimeoutMonitorTest 9/9 (synchronized API) |
| jwcore-adapter-jforex | SUCCESS | — | Szkielet, brak egzekucji |
| jwcore-adapter-cq | SUCCESS | 5 | Sequence API z AtomicLong (3C), contract tests |
| jwcore-execution-common | SUCCESS | 25 | Deterministyczny idempotency key |
| jwcore-execution-crypto | SUCCESS | 22 | — |
| jwcore-execution-forex | SUCCESS | 22 | — |
| jwcore-risk-coordinator | SUCCESS | 33 (28 passed, 5 @Disabled) | 5 testów do przepisania w 4B (DŁUG-318) |

**Łącznie:** 212 testów zielonych, 5 celowo wyłączonych (`@Disabled`), 0 failed, 0 errors.

## Kluczowe komponenty

### ExposureLedger (Paczka 4A, jwcore-risk-coordinator)

Pełna pozycja finansowa per `CanonicalId`:
- `netPosition` — BigDecimal, + long, − short
- `averageEntryPrice` — VWAP SCALE=8, HALF_UP
- `realizedPnL` — netto (commission odejmowana od każdego filla)
- `totalCommission` — skumulowana
- `totalExposure()` — suma abs(netPosition × avgEntryPrice)
- `marginUsed()` — totalExposure × MARGIN_RATE (0.01)
- `intentCount(canonicalId)` — licznik agregatowy pending intents

Reguły księgowania:
- Otwarcie pozycji: netPosition i avgEntryPrice z filla
- Dodawanie: VWAP na wartościach bezwzględnych
- Reverse/close: realizedPnL z różnicy ceny × zamknięta ilość
- Pełne zamknięcie: netPosition = 0, avgEntryPrice = ZERO (nie null)

Fail-fast `IllegalStateException` przy terminalnym evencie (cancel/reject/fill) bez pending intent — polityka MVP 4A dla lokalnie spójnego event stream. NIE dotyczy reconcile po reconnect (to 4B1).

### BrokerSession reconnect/reconcile (Paczka 4B1, jwcore-adapter-jforex)

Mechanizm reconnect po zerwaniu połączenia + emisja `BrokerReconcileEvent` po stabilizacji. Polityka reconcile rozszerzona poza fail-fast `IllegalStateException` z 4A. **DŁUG-314 zamknięty.**

### RiskCoordinator escalation (Paczka 4C1, jwcore-risk-coordinator)

`SAFE→HALT` przy powtórzonym `OrderUnknown` w oknie czasowym + `RiskStateResetCommand` do manualnego resetu. `ITimeProvider` dla testów deterministycznych. Wariant D Architekta wdrożony.

### AlertEvent + adapter-alerting (Paczka 4C2/A, jwcore-adapter-alerting)

`AlertType` (HALT, PERMANENT_FAILURE), Telegram bot + JUL panel. Tekst alertów zgodny z zasadą 37 (system autonomiczny, brak presji „natychmiast").

### ProcessingFailureEmitter (Paczka 4C2/B, ADR-017 Faza 1)

Error isolation w `RiskCoordinatorEngine` — wyjątki przy przetwarzaniu eventów nie wywalają silnika, są journalowane jako `EventProcessingFailedEvent`. Ujednolicenie wzorca `try/catch → emit → continue` z `ExecutionRuntime`.

### Auto-eskalacja SAFE (Paczka 4C2/C, ADR-017 Faza 2)

`EventProcessingFailedEvent` v3 (5 nowych pól: `attemptNumber`, `isPermanent`, `sourceModule`, `originalEventType`, `failedAccountId`), `AlertEvent` v2 (multi-account z `affectedAccounts`), `FailureCounter` (LRU 10k + TTL 24h, journal jako source of truth), auto-eskalacja przy permanent failure (3 attempts), atomicity copy-before-commit dla map silnika.

### OrderTimeoutMonitor (Paczka 4A, jwcore-core)

Synchronizacja:
- `registerPending()`, `markTerminal()`, `scanTimeouts()`, `pendingCount()` — wszystkie `synchronized`
- `scanTimeouts()`: pod lockiem tylko zbieranie i usuwanie z mapy; emisja `eventJournal.append()` POZA sekcją krytyczną
- Cel: brak deadlock/re-entrancy, brak blokowania innych wątków

Test wielowątkowy: 4 wątki × 1000 iteracji, `CountDownLatch` jako bariera startowa, callback z blokadą do weryfikacji emisji poza lockiem.

### Sequence API (Paczka 3C, jwcore-adapter-cq)

- `AtomicLong` counter w `ChronicleQueueEventJournal`
- `EventEnvelope.sequenceNumber` (rename z `timestampMono` w 3D)
- `readAfterSequence(long sequence)` — O(n) filter (DŁUG-317: O(log n) w przyszłości)
- Tailer offset persistence: `RiskCoordinatorTailer` używa sequence API (DŁUG-311 zamknięty)

### RiskCoordinatorEngine

Po adaptacji 4A:
- `apply(EventEnvelope)` przekazuje eventy do ledgera
- Mostek accountId → canonicalId (wewnętrzna mapa)
- `exposureSnapshot()` — skumulowana mapa per account
- 5 testów @Disabled — do przepisania w 4B (DŁUG-318)

## Wire compatibility / kompatybilność binarna

- `EventEnvelope.sequenceNumber` — rename z `timestampMono`, serializacja pozycyjna BinaryCodec zachowana
- `EventType`: OrderFilledEvent i OrderCanceledEvent dopisane NA KOŃCU — stare ordinals bez zmian
- `OrderFilledEvent`: nowy kształt (10 pól), stary wire nieważny — **BREAKING** dla logów sprzed 4A jeśli takie były
- `OrderCanceledEvent`: nowy kształt (6 pól), **BREAKING** dla logów sprzed 4A jeśli takie były

## Braki świadome MVP 4A

1. `intentCount` — agregat per canonicalId, nie per orderId. TODO pending-by-orderId jeśli potrzebny
2. `remainingQuantity` w OrderFilledEvent — przechowywane i walidowane, ale nie używane do księgowania w 4A
3. `totalExposure` / `marginUsed` używają avgEntryPrice, nie `lastKnownPrice` — TODO po integracji market data
4. `MARGIN_RATE = 0.01` jako stała globalna — TODO per-instrument

## Polityka build-fix (nowe od 22.04.2026)

Zgodnie z zasadą #28 (WYJATEK BUILD-FIX CODE):
- Dla trywialnych błędów kompilacji/syntax (final, dostosowanie wywołań do nowego API, importy) — Code może sam poprawić pod kontrolą Claude QG
- Zabronione: zmiana logiki biznesowej, nowa logika księgowania/ryzyka, nowe testy, nowe API
- Claude QG opisuje zakres w prompcie, Code raportuje każdą zmianę

Historia 4A: 2 iteracje build-fix przez Codex (final w teście, stare API w konsumencie) + 1 iteracja Code (dopasowanie konstruktorów w testach + @Disabled dla 5 testów pod 4B).

## Co dalej

1. Paczka 4D — Advanced Stub Broker (simulator egzekucji, timeouty, reject, partial fill, stan konta + testy integracyjne execution runtime ↔ stub broker). **Ostatni bloker PoC JForex Etap 1.**
2. Paczka 5 — PoC JForex Etap 1 (adapter JForex SDK, mapowanie eventów, demo Dukascopy).
