# Podsumowanie Projektu JWCore

**Wersja:** 2.2 (22.04.2026 wieczór, po zamknięciu Paczki 4A)
**Autor:** Zespół AI (GPT, Gemini, Claude) + Architekt
**Status:** W aktywnej realizacji

---

## Cel projektu

JWCore — własny system tradingowy w Javie 21, zastępujący infrastrukturę MT5. Pierwszy adapter: Dukascopy Bank SA przez JForex SDK. Docelowo: FIX API dla wielu brokerów.

Budowany przez zespół AI (Principal Engineer, Chief Technical Architect, Quality Gate, Code Generator, Implementation Agent) pod kierownictwem Architekta. Komunikacja całkowicie po polsku.

## Infrastruktura

| Element | Lokalizacja | Rola |
|---------|-------------|------|
| Repo prywatne | github.com/jun564/jwcore | Źródło prawdy |
| Repo publiczne | github.com/jun564/jwcore-review | Auto-sync po pushu do main, dla connectorów GPT/Gemini |
| VPS Hetzner (Ubuntu) | Orchestrator/API | Infrastruktura kontrolna |
| VPS ForexVPS (Windows) | Live trading | Produkcja |
| VPS Contabo (Windows Server 2025) | Optymalizacja | Równoległy optymalizator strategii |
| Klon lokalny | `C:\Users\janus\Desktop\Janusz\SIT Polska\TRADEROWO\schematy AI\Farma JWCore\repo\jwcore` | Lokalizacja Architekta |

Demo konta MT5 (Contabo, optymalizacja) — archiwum infrastruktury MT5, zachowane dla testów.

## Stan sprintów

### Sprint 3.1 — ZAMKNIĘTY

- 3.1.1 — Audit
- 3.1.2 — Domain lifecycle events + RejectReason

### Sprint 3.2 — W REALIZACJI

| Paczka | Status |
|--------|--------|
| 1 — OrderEventEnvelope + BinaryCodec | ZAMKNIĘTA |
| 2 — ChronicleQueueEventJournal MVP | ZAMKNIĘTA |
| 3A — Domain lifecycle events | ZAMKNIĘTA |
| 3B — ExposureLedger MVP + SAFE + idempotency | ZAMKNIĘTA |
| 3C — Sequence API + Tailer offset | ZAMKNIĘTA |
| 3D — Rename timestampMono → sequenceNumber | ZAMKNIĘTA |
| 4A — ExposureLedger pełna pozycja + lifecycle + OrderTimeoutMonitor sync | ZAMKNIĘTA |
| 4B — Reconnect/reconcile + przepisanie 5 testów RiskCoordinator | W PLANIE (blokuje PoC) |
| 4C — Error isolation + SAFE/HALT alerting | W PLANIE |
| 4D — Advanced Stub Broker | W PLANIE (warunek PoC) |
| 5 — PoC JForex (Etap 1, demo) | ZABLOKOWANA 4B+4D |

## Kluczowe komponenty (stan na 22.04.2026 wieczór)

### Warstwa domain
- `EventEnvelope` — pozycyjny BinaryCodec, sequenceNumber zamiast timestampMono
- `OrderIntentEvent`, `OrderSubmittedEvent`, `OrderRejectedEvent` — z lifecycle
- `OrderFilledEvent` — nowy kształt 4A (10 pól, OrderSide, filledQuantity, averagePrice, commission, remainingQuantity, itd.)
- `OrderCanceledEvent` — nowy kształt 4A (6 pól, reason z dozwolonego zbioru)
- `OrderSide` — BUY/SELL

### Warstwa core
- `ChronicleQueueEventJournal` — implementacja z AtomicLong sequence counter
- `OrderTimeoutMonitor` — zsynchronizowany, emisja poza lockiem (4A, DŁUG-305 zamknięty)
- `IEventJournal`, `AbstractEventJournalContractTest` — kontrakty

### Warstwa risk coordinator
- `ExposureLedger` — pełna pozycja finansowa per canonicalId (netPosition, avgEntryPrice VWAP, realizedPnL netto, totalCommission, totalExposure, marginUsed, intentCount) + fail-fast przy brakującym pending intent (DŁUG-309 zamknięty)
- `RiskCoordinatorEngine` — dostosowany do nowego API ExposureLedger; mostek accountId → canonicalId
- `RiskCoordinatorTailer` — offset persistence przez sequence API (DŁUG-311 zamknięty)

### Warstwa execution
- `execution-common` — deterministyczny idempotency key
- `execution-crypto` — szkielet (testy zielone)
- `execution-forex` — szkielet (testy zielone)

### Warstwa adapter
- `adapter-cq` — Chronicle Queue adapter (5 testów kontraktowych)
- `adapter-jforex` — szkielet bez egzekucji

## Zespół AI

| Członek | Rola | Model | Dostęp do kodu |
|---------|------|-------|----------------|
| Gemini | Chief Technical Architect | — | Import kodu (re-import per iteracja) |
| GPT | Principal Engineer | — | Connector GitHub (per-file) |
| Claude | Quality Gate Coordinator | Opus 4.7 | git clone + connector |
| Codex | Code Generator (OpenAI) | — | Git + autopush po „Utwórz PR" |
| Claude Code | Implementation Agent | — | Git + PAT (pełny dostęp zapisu) |

Architekt — decyzja finalna, właściciel projektu.

## Zasady obowiązujące

Lista zasad: `docs/ZASADY_WSPOLPRACY.md` v1.2 (22.04.2026) — 35 zasad.

Najistotniejsze świeże (22.04.2026):
- Zasada 30 — walidacja zależności w promptach Codex (Codex nie ma Mavena, pisze na ślepo)
- Zasada 31 — wyjątek build-fix dla Code (pragmatyka, po 2 iteracjach build-fix w 4A)

## Długi techniczne — priorytety

**BLOKUJĄCE PoC JForex:** brak (DŁUG-314 zamknięty w 4B1).

**Pozostałe:** DŁUG-301, 302, 303, 304, 306, 307, 308, 310, 312, 315, 317 — otwarte, żaden nie blokuje PoC.

**Zamknięte 22.04–25.04.2026:** DŁUG-305 (OrderTimeoutMonitor sync), DŁUG-309 (pełna pozycja finansowa), DŁUG-311 (tailer offset), DŁUG-313 (rename), DŁUG-316 (workflow sync-to-review), DŁUG-314 (BrokerSession reconnect/reconcile, 4B1 PR #18, 23.04), DŁUG-318 (5 testów @Disabled przepisane, 4B2 PR #19, 23.04). Paczki: 4B1, 4B2, 4C1, 4C2/A, 4C2/B (ADR-017 Faza 1), 4C2/C (ADR-017 Faza 2).

## Historia 22.04.2026 (skrót)

1. Pakiet dokumentacji v2.0 (7 docx + 6 md) → main
2. Incydent bezpieczeństwa PAT → odwołany, nowy w Credential Manager, klon repo w stałej lokalizacji
3. Workflow sync-to-review (DŁUG-316) → naprawione
4. Paczka 3C v2 (sequence API z AtomicLong) → merge po fix test-jar
5. Paczka 3D (rename) → merge (czysty refactor)
6. Paczka 4A (ExposureLedger pełna pozycja + lifecycle events + OrderTimeoutMonitor sync) → merge po 3 iteracjach build-fix
7. Pakiet dokumentacji v2.1 wieczorny → w przygotowaniu (ten dokument)

## Co dalej

1. Paczka 4D — Advanced Stub Broker (ostatni bloker PoC JForex Etap 1)
2. Paczka 5 — PoC JForex Etap 1 (Dukascopy demo, mandatory)
