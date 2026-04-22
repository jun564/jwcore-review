# Stan Implementacji JWCore

**Snapshot: 22.04.2026 rano**

Dokument operacyjny mapujący aktualny stan implementacji projektu JWCore. Synchronizowany z `Doc7 — Stan Implementacji v1.0` (docx, archiwum formalne u Architekta).

Aktualizowany po każdym zmergowanym PR. Pierwszeństwo ma ten dokument dla pytań "jak jest teraz w kodzie".

---

## Moduły projektu

Kod JWCore jest zorganizowany w Maven multi-module (ADR-014). 7 modułów:

| Moduł | Zawartość | Status |
|---|---|---|
| **jwcore-domain** | EventEnvelope, EventType, CanonicalId, IdempotencyKeys, RejectReason, OrderIntent, OrderSubmittedEvent, OrderFilledEvent, OrderCanceledEvent, OrderRejectedEvent, OrderTimeoutEvent, OrderUnknownEvent, RiskDecisionEvent, StateRebuiltEvent, EventProcessingFailedEvent, MarginUpdateEvent | 🟢 STABILNY (main) |
| **jwcore-core** | IEventJournal, ITimeProvider, RealTimeProvider, ControllableTimeProvider, TailSubscription, OrderTimeoutMonitor, BackpressureController, AbstractEventJournalContractTest (nowe w 3C) | 🟢 STABILNY, 3C rozszerza |
| **jwcore-adapter-cq** | ChronicleQueueEventJournal, ChronicleQueueJournalConfig | 🟡 3C build FAILED (CQ append) |
| **jwcore-execution-common** | EventEmitter, IntentRegistry, IntentPhase, PendingIntent, ExecutionState, ExecutionStateResolver | 🟢 STABILNY (main) |
| **jwcore-execution-crypto** | Stub modułu crypto | 🟡 STUB |
| **jwcore-execution-forex** | ExecutionRuntime, BrokerSession interface, StubBrokerSession, LocalRiskPolicy, ExposureLedger | 🟢 STABILNY (JForex pending) |
| **jwcore-risk-coordinator** | RiskCoordinatorEngine, RiskCoordinatorTailer, Main, ExposureLedger, RiskDecisionEmitter | 🟡 3C refactor tailera |

### Zależności

```
jwcore-domain (fundament)
    ↓
jwcore-core
    ↓
jwcore-adapter-cq, jwcore-execution-common
                        ↓
            jwcore-execution-crypto, jwcore-execution-forex
                        ↓
                jwcore-risk-coordinator
```

### Moduły planowane (nie istnieją jeszcze)

- `jwcore-adapter-jforex` — adapter Dukascopy JForex (Etap 1/2)
- `jwcore-strategy-host` — host dla 20 strategii (Etap 3)
- `jwcore-control` — backend dashboard (Etap 4)
- `jwcore-optimizer` — optymalizator parametrów (Etap 5)
- `jwcore-adapter-fix` — adapter FIX API (Etap 6)

---

## Historia sprintów

| Sprint | Zakres | Status |
|---|---|---|
| **Sprint 1** | Fundament: jwcore-domain + jwcore-core + jwcore-adapter-cq | 🟢 ZAMKNIĘTY (main) |
| **Sprint 2** | Execution layer (common + crypto + forex stub), ADR-011 | 🟢 ZAMKNIĘTY (main) |
| **Sprint 3.1** | risk-coordinator scaffolding | 🟢 ZAMKNIĘTY (main) |
| **Sprint 3.1.1** | ADR-015 Event Correlation + sourceProcessId | 🟢 ZAMKNIĘTY (main) |
| **Sprint 3.1.2** | Paczki 1-8 — audyt + fixy + rozbudowa | 🟢 ZAMKNIĘTY (main) |
| **Sprint 3.2** | Paczki 1-3B w main, 3C build FAILED, 3D zatwierdzona | 🟡 W TRAKCIE |

### Szczegóły Sprint 3.2

**Zmergowane do main:**
- Paczka 1 — Audyt architektoniczny (DŁUG-301 do 308)
- Paczka 2 — Fixy rejestru (KRYT-001, WYS-001/002, KRYT-003A)
- Paczka 3A — Domain lifecycle events
- Paczka 3B — ADR-016 Risk Decision + ExposureLedger + SAFE (MVP)

**W trakcie:**
- Paczka 3C — IEventJournal sequence API + RiskCoordinatorTailer offset
  - Gałąź: `codex/rozszerz-ieventjournal-o-sequence-api`
  - Status: BUILD FAILED (10 testów ChronicleQueueEventJournalContractTest)
  - Problem: `documentContext.index()` ≠ `appender.lastIndexAppended()` w CQ 5.25ea16
  - Rozwiązanie: przeprojektowanie CQ append wymaga decyzji zespołu w osobnej sesji

**Zatwierdzona, następna:**
- Paczka 3D — rename `EventEnvelope.timestampMono` → `sequenceNumber`
  - Hard dependency po merge 3C
  - Dotyka 30-40 plików
  - DŁUG-313

**Backlog Sprint 3.2 (po 3C+3D):**
- A) Eskalacja SAFE→HALT przy powtórzonym OrderUnknown
- B) Panel + Telegram alerting SAFE/HALT (ADR-010)
- C) Manualny reset SAFE→RUN
- D) DŁUG-309 — pełna pozycja finansowa ExposureLedger (BLOKUJE PoC JForex)
- E) ADR-017 error isolation per-event
- F) POM-001 crash-recovery timeoutów
- G) OrderFilledEvent + OrderCanceledEvent pełny lifecycle
- H) Advanced Stub Broker przed PoC JForex

---

## Stan implementacji ADR

Audyt wykonany 22.04.2026 przez Claude Code.

| ADR | Temat | Status |
|---|---|---|
| ADR-001 | Model czasu | 🟢 ZAIMPLEMENTOWANY |
| ADR-002 | Lifecycle zlecenia | 🟢 ZAIMPLEMENTOWANY |
| ADR-003 | Reconnect / reconcile | 🟡 CZĘŚCIOWO (DŁUG-314) |
| ADR-004 | Hot path / control plane | 🟡 CZĘŚCIOWO (DŁUG-315) |
| ADR-005 | SAFE/HALT/KILL | 🟢 ZAIMPLEMENTOWANY (jako ADR-011) |
| ADR-006 | Executor per kolejka CQ | 🟢 ZAIMPLEMENTOWANY |
| ADR-007 | — (numer przeskoczony) | ⚫ NIE ISTNIEJE |
| ADR-008 | Execution jako jedyny rebuilder | 🟢 ZAIMPLEMENTOWANY |
| ADR-009 | OrderTimeoutEvent | 🟢 ZAIMPLEMENTOWANY |
| ADR-010 | Margin Monitor w GUI | ⏳ OCZEKUJE (Etap 4) |
| ADR-011 | SAFE/HALT/KILL per rachunek | 🟢 ZAIMPLEMENTOWANY |
| ADR-012 | Pinning CQ 5.25ea16 | 🟢 ZAIMPLEMENTOWANY |
| ADR-013 | Obowiązkowe flagi JVM | 🟢 ZAIMPLEMENTOWANY |
| ADR-014 | Layout repo | 🟢 ZAIMPLEMENTOWANY |
| ADR-015 | Event Correlation | 🟢 ZAIMPLEMENTOWANY |
| ADR-016 | Risk Decision + ExposureLedger | 🟢 ZAIMPLEMENTOWANY (MVP) |
| ADR-017 | Infrastructure Audit | 🟡 W TRAKCIE (Etap 1) |

**Statystyki:**
- 13/16 ADR-ów — ZAIMPLEMENTOWANE w pełni
- 2/16 ADR-ów — CZĘŚCIOWO (ADR-003 reconnect, ADR-004 hot path)
- 1/16 ADR-ów — OCZEKUJE (ADR-010 Margin Monitor, Etap 4)
- 1/16 ADR-ów — W TRAKCIE (ADR-017 Infrastructure Audit)

### Długi wynikające z częściowej implementacji

- **DŁUG-314** — Dokończenie reconnect/reconcile w BrokerSession (ADR-003)
- **DŁUG-315** — TokenBucket rate limiter (ADR-004 częściowe)

---

## Infrastruktura

### Serwery

| Serwer | OS | Rola | Status |
|---|---|---|---|
| Hetzner VPS | Ubuntu | Orkiestrator, API | 🟢 Aktywny |
| ForexVPS | Windows | Live trading (JForex docelowo) | 🟡 Puste |
| Contabo VPS | Windows Server 2025 → Ubuntu | Optymalizacja | 🟡 Migracja |

### Repo GitHub

- `jun564/jwcore` — prywatne, główne repo kodu
- `jun564/jwcore-review` — publiczne, auto-sync po pushu do main
- HEAD main: commit `6f1075e` (Paczka 3B Sprint 3.2)

### Otwarte gałęzie

- `codex/rozszerz-ieventjournal-o-sequence-api` — Paczka 3C, build FAILED, pending

---

## Zewnętrzne zależności

### Biblioteki Java
- Java 21 LTS
- Chronicle Queue 5.25ea16 (pinowane ADR-012)
- JUnit Jupiter 5.10.2
- JaCoCo
- Maven 3.9+

### Usługi
- GitHub — repo i CI/CD
- Dukascopy Bank SA — broker docelowy (KYC pending)
- JForex SDK — biblioteka (pobranie po aktywacji)
- PostgreSQL — baza (Etap 4, nie zainstalowana)
- Telegram Bot API — alerting (Etap 4)

### Pending Architekta
- KYC do Dukascopy
- Regeneracja tokenu GitHub (blokuje ADR-017 Etap 1)
- Instalacja Docker Desktop (blokuje Etap 5)

---

## Aktywne długi techniczne

Pełna lista w `docs/sprint3-backlog.md` i `Doc6 v1.1` (docx). Podsumowanie krytycznych i wysokich:

### 🔴 Krytyczne (blokują Etap 1)
- **DŁUG-309** — Pełna pozycja finansowa w ExposureLedger (blokuje PoC JForex)

### 🟠 Wysokie
- **DŁUG-311** — RiskCoordinatorTailer offset (w trakcie Paczka 3C)
- **DŁUG-313** — Rename timestampMono → sequenceNumber (Paczka 3D)
- **DŁUG-314** — Reconnect/reconcile w BrokerSession (ADR-003)

### 🟡 Średnie (8 pozycji)
DŁUG-301, 303, 305, 306, 307, 308, 310, 312, 315

### 🟢 Niskie (2 pozycje)
DŁUG-302, 304

---

## Warunki wejścia do kolejnych etapów

### Etap 1 → Etap 2 (Adaptery + Risk Engine)
- [ ] Paczka 3C zmergowana
- [ ] Paczka 3D zmergowana
- [ ] DŁUG-309 zamknięty (pełen model portfelowy)
- [ ] Advanced Stub Broker zaimplementowany
- [ ] KYC Dukascopy zakończone
- [ ] Podłączenie JForex SDK + 72h test stabilności
- [ ] README Etap 1

### Etap 2 → Etap 3 (Strategy Host)
- [ ] 5 adapterów brokera przetestowanych na DEMO
- [ ] Risk Engine odrzuca nieprawidłowe intencje
- [ ] Virtual Account Manager izoluje budżety
- [ ] DŁUG-314 zamknięty (pełen reconnect/reconcile)
- [ ] DŁUG-315 zamknięty (TokenBucket)

### Etap 3 → Etap 4 (GUI)
- [ ] 1 strategia stabilnie na DEMO przez 7 dni
- [ ] Reconciliation działa
- [ ] Zero ostrzeżeń w logach przez 7 dni

### Etap 4 → Etap 5 (Optymalizator)
- [ ] GUI operacyjnie kompletne
- [ ] Monitoring działa
- [ ] Moduł podatkowy generuje PIT-38

### Etap 5 → Etap 6 (FIX + 20 strategii)
- [ ] Pierwsza optymalizacja BTC z sensownymi wynikami
- [ ] Architekt decyzja o przeniesieniu kapitału ($100k+ u Dukascopy)

---

**Dokument kanoniczny:** `Doc7 — Stan Implementacji v1.0` (docx u Architekta)
**Wersja:** 1.0
**Snapshot:** 22.04.2026
