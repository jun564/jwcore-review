# Sprint 3 — Backlog iteracji

**Data:** 18.04.2026
**Status:** Plan obowiązujący, Sprint 3.1 w trakcie (jutro nowy czat GPT)
**Zatwierdzony przez:** Architekt, Claude, Gemini, GPT (ustnie, 18.04.2026)

---

## Kontekst podziału

Sprint 3 pierwotnie miał być dostarczony w jednej iteracji. GPT (Principal Engineer) 18.04.2026 nie był w stanie dostarczyć pełnego zakresu w jednej turze — dostarczał paczki niespełniające kontraktu. Zespół zdecydował o podziale Sprintu 3 na trzy iteracje.

**Warunek zamknięcia Sprintu 3:** wszystkie trzy iteracje (3.1 + 3.2 + 3.3) muszą być zielone. Sprint 4 (Strategy Host) nie startuje po 3.1 ani 3.2.

**Zasada:** każda iteracja osobno spełnia kontrakt jakościowy:
- Kompletna paczka ZIP
- `mvn clean verify` BUILD SUCCESS
- Branch coverage ≥80% w nowych modułach
- README + RAPORT per iteracja
- Quality Gate review przez Claude + Gemini przed startem kolejnej

---

## Iteracja 3.1 — Fundament execution + risk coordinator szkielet

### Zakres

**1. `jwcore-execution-common` (nowy moduł):**
- `ExecutionState` enum (RUN, SAFE, HALT, KILL) + `moreRestrictive()`
- `ExecutionStateResolver` — resolver konfliktu decyzji lokalnych vs globalnych (hierarchia KILL>HALT>SAFE>RUN)
- `OrderTimeoutTracker` — rejestr intencji + `checkTimeouts()` z callbackiem
- `IntentRegistry` — mapowanie intent_id ↔ canonical_id, **SPIĘTY** z OrderTimeoutTracker
- `EventEnvelope` — pełna struktura (event_id UUID v4, payload_version, timestamps, idempotency_key)
- `OrderTimeoutEvent` — pełna struktura wg ADR-009
- `StateRebuiltEvent` — pełna struktura wg ADR-008 (RebuildType enum + Discrepancy record)
- `EventEmitter` nad IEventJournal

**2. `jwcore-execution-crypto` (nowy moduł):**
- `ExecutionRuntime` z **pełnym** tickCycle (nie skeleton):
  a) Czytanie oczekujących `RiskDecisionEvent` z CQ
  b) Pobranie lokalnych decyzji ryzyka (Poziom 1-2)
  c) `ExecutionStateResolver` → najbardziej restrykcyjny stan
  d) Zastosowanie stanu (state transition)
  e) Obsługa `OrderIntent` w stanie RUN
  f) Sprawdzenie timeoutów → emisja `OrderTimeoutEvent`
  g) Emisja `MarginUpdateEvent` co N cykli
- `main()` z graceful shutdown (SIGTERM handler → GracefulShutdownCoordinator)
- Konfiguracja z `execution-crypto.properties`
- **Stub** `BrokerSession` (tylko interfejs, implementacja pełna w 3.2)

**3. `jwcore-execution-forex` (nowy moduł):**
- Identyczna struktura jak crypto, różnice tylko w konfiguracji

**4. `jwcore-risk-coordinator` (nowy moduł):**
- `RiskCoordinatorEngine` z `evaluate()` dla total exposure
- Tailer CQ (events-business + market-data) — struktura kompletna, logika cross-account zostaje w 3.3
- `main()` z graceful shutdown
- Konfiguracja z `risk-coordinator.properties`

### Testy 3.1 (obowiązkowe, ≥80% branch coverage)

- `ExecutionStateTest` — wszystkie permutacje moreRestrictive() (4x4 = 16 przypadków)
- `ExecutionStateResolverTest` — wszystkie kombinacje lokalny/globalny dla RUN/SAFE/HALT/KILL
- `OrderTimeoutTrackerTest` — rejestracja, wyzwolenie timeout, brak timeout w zakresie, cleanup
- `IntentRegistryTest` — bind, lookup po intent_id, lookup po canonical_id, usunięcie
- `StateMachineTest` — legalne przejścia (RUN↔SAFE↔HALT→KILL), nielegalne przejścia
- `ExecutionRuntimeTest` — mock IEventJournal + mock BrokerSession + ControllableTimeProvider, pełny cykl w różnych stanach
- `EventEnvelopeTest`, `OrderTimeoutEventTest`, `StateRebuiltEventTest` — walidacja struktur, equals/hashCode

### Definition of Done 3.1

- ✅ `jwcore-sprint3-iteracja-3.1.zip` kompletna paczka
- ✅ `mvn clean verify` BUILD SUCCESS we wszystkich modułach (weryfikacja przez Code na jwcore-live-01)
- ✅ Tabela JaCoCo per moduł, branch ≥80%
- ✅ `README_Sprint_3.1.md`
- ✅ `RAPORT_Sprint_3.1.md`
- ✅ Quality Gate review Claude + Gemini → zielone światło na 3.2

---

## Iteracja 3.2 — JForex pełny + Reconciliation

### Zakres

**1. `jwcore-adapter-jforex` (rozszerzenie obecnego stub):**
- `JForexBrokerSession` — **async** (nie Thread.sleep), z listenerami
  - Connect / Disconnect / Reconnect logic (exponential backoff, max N prób)
  - `ISystemListener` → emisja `BrokerSessionConnectedEvent`, `BrokerSessionLostEvent`, `BrokerSessionReconnectedEvent`
- `IStrategy` implementacja — emisja ticków do IEventJournal (kolejka market-data)
- Order send pipeline (OrderIntent → JForex → terminalne eventy)
- Obsługa terminalnych eventów brokera:
  - `OrderFilledEvent`
  - `OrderRejectedEvent`
  - `OrderCanceledEvent`

**2. `OrderIntent` pipeline pełny:**
- OrderIntent → IntentRegistry binding canonical_id
- OrderIntent → JForexBrokerSession.sendOrder()
- Terminalne eventy → unbind z registry + emisja do CQ
- Timeout path → Reconciliation Engine

**3. Reconciliation Engine (wbudowany w Execution):**
- Przy starcie procesu: query brokera o stan pozycji/orderów
- Porównanie z rekonstrukcją z CQ
- Wykrycie discrepancy → emisja w `StateRebuiltEvent`
- Przy `OrderTimeoutEvent`: query brokera o status konkretnego zlecenia
- Jeśli broker zna → emisja właściwego terminalnego eventu z opóźnieniem
- Jeśli broker nie zna → `OrderUnknownEvent` + eskalacja (Telegram)

**4. Sole State Rebuilder (ADR-008):**
- Pełna implementacja (nie szkielet)
- Snapshot + delta replay
- Emisja `StateRebuiltEvent` z RebuildType, events_replayed, discrepancies

**5. Market-data ingestion:**
- IStrategy onTick → IEventJournal.append (market-data queue)
- EventEnvelope dla każdego ticka

### Testy 3.2

- Symulowane scenariusze reconnect (mock JForex session)
- OrderIntent pipeline — happy path (fill) + timeout path + rejection
- Reconciliation — start z pustym state, start z discrepancy, OrderTimeout → query broker
- StateRebuild z pustym snapshot i z istniejącym snapshot
- Market-data volume test (10k ticków/s)
- Testy integracyjne na koncie DEMO Dukascopy

### Definition of Done 3.2

Jak 3.1, plus:
- Testy integracyjne na DEMO Dukascopy — zielone
- Audit trail: ręczne wysłanie OrderIntent → zlecenie widoczne na platformie Dukascopy → terminalny event w CQ
- Quality Gate review → zielone światło na 3.3

---

## Iteracja 3.3 — Risk cross-account + stabilność

### Zakres

**1. Pełna logika `RiskCoordinatorEngine`:**
- Total exposure (suma ekspozycji z obu rachunków)
- Korelacja instrumentów (gdy pozycje w skorelowanych instrumentach — redukcja limitu)
- Portfolio drawdown (%)
- Time-based limits (max X zleceń / godzinę cross-account)
- Progi konfigurowalne w `risk-coordinator.properties`

**2. `RiskDecisionEvent` dystrybucja:**
- Emisja do events-business CQ
- Subscription w Execution adapterach (crypto, forex)
- Priorytet: RiskDecisionEvent > lokalne decyzje (hierarchia ADR-011)

**3. `MarginUpdateEvent` emission:**
- Co 5 sekund (regularnie)
- Dodatkowo przy zmianie >2%
- Przy otwarciu/zamknięciu pozycji
- Per rachunek osobno

**4. Systemd unit files:**
- `jwcore-execution-crypto.service`
- `jwcore-execution-forex.service`
- `jwcore-risk-coordinator.service`
- Z pełnymi flagami JVM (ADR-013)
- Z Restart=always, RestartSec=5
- Lokalizacja logs: `/var/log/jwcore/`

**5. Test 72h stabilności:**
- Uruchomienie wszystkich 3 procesów na jwcore-live-01
- Symulacja pełnego workflow (OrderIntent → JForex DEMO → terminalne eventy)
- Monitoring: memory usage, CPU, thread count, CQ growth, disk I/O
- Brak memory leaks, brak wiszących wątków, brak restartów
- Loguj anomalie

### Testy 3.3

- Scenariusze konfliktu decyzji end-to-end (lokalny SAFE + globalny HALT → wygrywa HALT)
- ExposureLimit cross-account (mock eventów z obu rachunków, przekroczenie progu)
- PortfolioDrawdown calculation
- Graceful shutdown całego systemu (wszystkie 3 procesy zakończone w <10s po SIGTERM)
- Restart resilience (zabij proces → autorestart przez systemd → StateRebuild → normalna praca)

### Definition of Done 3.3

Jak 3.2, plus:
- Test 72h stabilności → **zielony**
- Systemd unit files działają (start, stop, restart, enable on boot)
- Sprint 3 formalnie zamknięty → Etap 1 zamknięty (po BL-005 KYC)

---

## Dług formalnie spisany (z poprzedniej iteracji Sprint 3)

### DŁUG-301 — Baseline Sprint 3 "wersja 0 foundation"
- GPT dostarczył paczkę bez ExecutionRuntime, pełnych eventów, testów, CQ wiring
- **Adresowane w 3.1** — kompletne ExecutionRuntime, wszystkie testy, pełne eventy

### DŁUG-302 — OrderTimeout bez canonical_id mapping
- W baseline był placeholder "UNKNOWN"
- **Adresowane w 3.1** — IntentRegistry SPIĘTY z OrderTimeoutTracker

### DŁUG-303 — JForex BrokerSession z Thread.sleep
- Narusza ADR-001 (no Thread.sleep)
- **Adresowane w 3.2** — JForexBrokerSession async z listenerami

### DŁUG-304 — Brak BrokerSession events (CONNECTED/LOST)
- Nie było eventów o zmianie statusu połączenia
- **Adresowane w 3.2** — osobne eventy w CQ

### DŁUG-305 — CQ wiring w Execution
- Brak pełnego podpięcia ExecutionRuntime do IEventJournal
- **Adresowane w 3.1** (czytanie) i 3.2 (pełny bidirectional)

### DŁUG-306 — RiskCoordinator uproszczony
- Tylko placeholder z jedną metodą evaluate()
- **Adresowane w 3.3** — pełna logika cross-account

### DŁUG-307 — Brak backpressure w CQ przy dużym tick rate
- Ryzyko przyznane przez GPT 18.04 — przy market-data volume system może się zakorkować
- **Adresowane w 3.3** — test 10k ticków/s + kalibracja progów backpressure

---

## Mapa długu → iteracja

| Dług | Iteracja naprawcza |
|---|---|
| 301 (wersja 0 foundation) | 3.1 |
| 302 (canonical_id) | 3.1 |
| 303 (Thread.sleep JForex) | 3.2 |
| 304 (BrokerSession events) | 3.2 |
| 305 (CQ wiring) | 3.1 + 3.2 |
| 306 (Risk uproszczony) | 3.3 |
| 307 (backpressure test) | 3.3 |

---

## Ryzyka iteracji

### 3.1
- **Nowy czat GPT + onboarding** — pierwsza iteracja może wymagać kalibracji
- **Wariant wykonawczy 1** (GPT kod → Code build) — nowy flow, może być wolniejszy niż bezpośrednia dostawa

### 3.2
- **JForex SDK niestabilne w reconnect** — ryzyko przyznane przez GPT. Mitygacja: watchdog + retry logic + testy symulowanej utraty połączenia.
- **Testy na DEMO** — wymaga aktywnego konta Dukascopy demo

### 3.3
- **Test 72h na Infomaniak** — pierwszy długi test, może odkryć niespodziewane issues
- **Kalibracja progów backpressure** — empiryczne, może wymagać kilku przebiegów

---

## Harmonogram szacunkowy

- **3.1:** 19-24.04.2026 (koniec kwietnia)
- **3.2:** 25.04 – 10.05.2026 (pierwsza dekada maja)
- **3.3:** 11-31.05.2026 (koniec maja)
- **Test 72h:** 1-3.06.2026
- **Etap 1 zamknięty:** początek czerwca 2026

Szacunek przy założeniu 5h/dobę dostępności Architekta i sprawnej komunikacji z GPT w nowym czacie. Rewizja po 3.1.
