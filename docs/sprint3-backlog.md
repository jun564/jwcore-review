# Sprint 3 — backlog

**Aktualizacja: 22.04.2026**

Dokument operacyjny bieżącego stanu sprintów implementacyjnych Etapu 1 (PoC JForex). Synchronizowany z `Doc5 v1.1` (Plan Prac) oraz `Doc6 v1.1` (Backlog).

---

## Sprint 3.1.2 — ZAMKNIĘTY

Zmergowany do main 21.04.2026 nocą (przez inną instancję Claude + Codex).

**Paczki:**
- Paczka 1 — Audyt architektoniczny (10 znalezisk → DŁUG-301 do 308)
- Paczka 2 — Fixy rejestru (KRYT-001, WYS-001/002, KRYT-003A)
- Paczki 3-8 — rozbudowa (szczegóły w sesja-2026-04-21.md)

---

## Sprint 3.2 — W TRAKCIE

### Zmergowane do main

| Paczka | PR | Zakres | Commit HEAD |
|---|---|---|---|
| Paczka 1 | #10 | Audyt architektoniczny Sprint 3.1.2 | — |
| Paczka 2 | #11 | Fixy rejestru | — |
| Paczka 3A | #12 | Domain lifecycle events | — |
| Paczka 3B | #13 | ADR-016 + ExposureLedger + SAFE (MVP) | 6f1075e |

### W trakcie

**Paczka 3C — IEventJournal sequence API + RiskCoordinatorTailer offset**

- Gałąź: `codex/rozszerz-ieventjournal-o-sequence-api`
- Status: 🔴 BUILD FAILED
- Problem: ChronicleQueueEventJournal.append() — `documentContext.index()` ≠ `appender.lastIndexAppended()` w CQ 5.25ea16
- Symptomy: 10 testów `ChronicleQueueEventJournalContractTest` fail runtime z komunikatem `Assigned sequence mismatch, expected: XXX, actual: YYY` (różnica ~4M = CQ koduje cycle+offset w 64-bit index)
- Rozwiązanie: wymaga przeprojektowania CQ append przez zespół Gemini+GPT w dedykowanej sesji

**Opcje do rozważenia przez zespół:**
1. Dwuetapowy zapis (dotychczas zakazany w prompcie)
2. Sequence poza payloadem CQ (adapter-level AtomicLong + mapping)
3. Inne API CQ z gwarancją poznania index przed zapisem

### Zatwierdzona, następna

**Paczka 3D — Rename EventEnvelope.timestampMono → sequenceNumber**

- Hard dependency po merge Paczki 3C
- Dotyka 30-40 plików w kodzie
- DŁUG-313
- Dedykowany PR, osobna kolejka (nie backlog)

### Backlog Sprint 3.2 (po 3C i 3D)

W tej kolejności:

- **A)** Eskalacja SAFE→HALT przy powtórzonym OrderUnknown (Wariant D Architekta)
- **B)** Panel + Telegram alerting SAFE/HALT (ADR-010)
- **C)** Manualny reset SAFE→RUN (RiskStateResetCommand)
- **D)** DŁUG-309 — pełna pozycja finansowa ExposureLedger (**BLOKUJE PoC JForex**)
- **E)** ADR-017 error isolation per-event w ExecutionRuntime
- **F)** POM-001 crash-recovery timeoutów
- **G)** OrderFilledEvent + OrderCanceledEvent pełny lifecycle
- **H)** Advanced Stub Broker przed PoC JForex

---

## Aktywne długi techniczne

### 🔴 Krytyczne
- **DŁUG-309** — Pełna pozycja finansowa w ExposureLedger (BLOKUJE PoC JForex)

### 🟠 Wysokie
- **DŁUG-311** — RiskCoordinatorTailer offset (w trakcie Paczka 3C)
- **DŁUG-313** — Rename timestampMono → sequenceNumber (Paczka 3D)
- **DŁUG-314** — Reconnect/reconcile w BrokerSession (ADR-003, NOWY z audytu 22.04)

### 🟡 Średnie
- DŁUG-301 — IntentPhase.canTransitionTo() niekompletny
- DŁUG-303 — EventEnvelope serializacja versioning
- DŁUG-305 — OrderTimeoutMonitor bez synchronizacji
- DŁUG-306 — ChronicleQueueEventJournal retention policy
- DŁUG-307 — EventEmitter bez circuit breaker
- DŁUG-308 — IntentRegistry bez TTL
- DŁUG-310 — OrderRejected vs OrderSubmitted (brak size)
- DŁUG-312 — Unifikacja 7 kopii InMemoryEventJournal
- DŁUG-315 — TokenBucket rate limiter (ADR-004, NOWY z audytu 22.04)

### 🟢 Niskie
- DŁUG-302 — CanonicalId walidacja formatów
- DŁUG-304 — IdempotencyKeys bez salt

---

## Warunki wejścia do PoC JForex

Przed uruchomieniem PoC JForex na rzeczywistym koncie DEMO Dukascopy muszą być zamknięte:

1. ✅ KRYT-001, KRYT-002, KRYT-003 (w toku w 3C)
2. ⏳ Paczka 3C (IEventJournal sequence API)
3. ⏳ Paczka 3D (rename timestampMono)
4. ⏳ DŁUG-309 (pełny model portfelowy w ExposureLedger)
5. ⏳ DŁUG-314 (reconnect/reconcile w BrokerSession, ADR-003)
6. ⏳ ADR-017 pozycja E (error isolation per-event)
7. ⏳ OrderFilledEvent + OrderCanceledEvent pełny lifecycle
8. ⏳ Advanced Stub Broker (warstwa pośrednia)
9. ⏳ KYC Dukascopy (Architekt)
10. ⏳ Panel + Telegram alerting (operacyjnie wymagane)

---

## Otwarte gałęzie i PR

- `codex/rozszerz-ieventjournal-o-sequence-api` — Paczka 3C, build FAILED, pending decyzji zespołu

---

**Ostatnia sesja:** 22.04.2026 (rano, dokumentacja pakietowa)
**Poprzednie sesje:** 21.04.2026 (wieczór, Paczki 1-3B), 20-21.04.2026 (noc, inna instancja — Sprint 3.1.2 Paczki 4-8), 18.04.2026 (sesja Claude 12, dokumentacja)
