# Podsumowanie Projektu JWCore

**Wersja 2.1 | Aktualizacja: 22.04.2026**

Dokument syntetyczny — szybki wstęp do projektu JWCore dla nowego członka zespołu lub agenta AI. Zastępuje `Podsumowanie_Projektu_JWCore_v2.md` z 21.04.2026.

---

## Czym jest JWCore

JWCore to proprietary system tradingowy w Javie 21, budowany od zera przez jednego Architekta we współpracy z zespołem AI. Cel docelowy: platforma do zarządzania farmą 20+ strategii tradingowych na forex/crypto, z własnym Risk Engine, GUI i modułem podatkowym.

**Broker docelowy:** Dukascopy Bank SA (JForex SDK → FIX API w Etapie 6).
**Instrumenty:** Start z BTC/USD. Docelowo: cross-asset portfolio.
**Model:** Event-sourcing, Hexagonal Architecture, wielomodułowy Maven.

**Historia:**
- Do kwietnia 2026 — prototyp farmy w MetaTrader 5 (EA robots), jako baza do koncepcji.
- 14.04.2026 — decyzja o porzuceniu MT5 i budowie własnego systemu w Java. Nazwa JGGK CORE.
- 18.04.2026 — rename na JWCore. Pakiet dokumentacji v1.0 (Doc1-Doc6).
- 21-22.04.2026 — Sprinty 1 → 3.2 w trakcie Etapu 1. Zespół rozszerzony o Codex. Dokumentacja v2.0 (Doc1-Doc7).

---

## Zespół

Wszyscy komunikują po polsku. Architekt ma ostatnie słowo.

- **Architekt** — właściciel projektu. Pracuje nie w pełnym wymiarze, godzi z pracą zawodową.
- **Gemini** — Chief Technical Architect. Projektowanie architektury.
- **GPT** — Principal Engineer. Propozycje implementacyjne.
- **Claude** — Quality Gate Coordinator. Synteza zespołu, dokumentacja, krytyka.
- **Codex** — Implementation Agent (kod). Implementuje paczki sprintów w repo.
- **Claude Code** — Implementation Agent (infrastruktura). SSH, build, merge, operacje.

Granica Codex vs Claude Code:
- **Codex** = kod aplikacji w repo jwcore (feature branches + PR).
- **Claude Code** = operacje systemowe (merge main, testy, SSH na serwery, backup).

Szczegóły: `docs/ZESPOL.md` i `docs/ZASADY_WSPOLPRACY.md`.

---

## Infrastruktura

### Serwery

| Serwer | OS | Rola |
|---|---|---|
| Hetzner VPS | Ubuntu | Orkiestrator, API główny |
| ForexVPS | Windows | Live trading (JForex docelowo) |
| Contabo VPS | Ubuntu (migracja z Windows) | Optymalizacja (Docker workers) |

### Repo

- `jun564/jwcore` — prywatne, główne repo
- `jun564/jwcore-review` — publiczne, auto-sync po pushu do main

---

## Stan projektu na 22.04.2026

### Etap 0 (Fundament) — ZAMKNIĘTY

Pozostałe otwarte sprawy Architekta (KYC Dukascopy, regeneracja tokenu GitHub, instalacja Docker Desktop) nie blokują prac implementacyjnych zespołu AI.

### Etap 1 (PoC JForex) — W TRAKCIE

Realizowany przez kolejne Sprinty implementacyjne:

| Sprint | Status |
|---|---|
| Sprint 1 (fundament) | 🟢 ZAMKNIĘTY |
| Sprint 2 (execution) | 🟢 ZAMKNIĘTY |
| Sprint 3.1 (risk-coordinator) | 🟢 ZAMKNIĘTY |
| Sprint 3.1.1 (Event Correlation) | 🟢 ZAMKNIĘTY |
| Sprint 3.1.2 (audyt + fixy) | 🟢 ZAMKNIĘTY |
| Sprint 3.2 (Paczki 1-3B+) | 🟡 W TRAKCIE (3C build failed) |

### Etapy 2-6

Oczekują. Szczegóły w `Doc5 v1.1` i `docs/STAN_IMPLEMENTACJI.md`.

---

## Pakiet dokumentacji

Obowiązują dwa źródła prawdy:

**Kanoniczne docx (u Architekta):**
- Doc1 v2.0 — Zasady Pracy Zespołu AI
- Doc2 — Podsumowanie Sesji Claude 12 (archiwalny)
- Doc3A v2.1 + Doc3B v2.1 — Architektura (baseline)
- Doc3 Uzupełnienie v2.2 — delta (stan impl + otwarte spory)
- Doc4 v2.0 — ADR-001 do ADR-017
- Doc5 v1.1 — Plan Prac + Pending Register
- Doc6 v1.1 — Backlog Problemów Technicznych (BL + DŁUG)
- Doc7 v1.0 — Stan Implementacji

**Operacyjne md (w repo):**
- `docs/ZASADY_WSPOLPRACY.md` — sync z Doc1 v2.0
- `docs/ZESPOL.md` — członkowie zespołu
- `docs/STAN_IMPLEMENTACJI.md` — sync z Doc7
- `docs/NARZEDZIA_ZESPOLU.md` — inwentaryzacja narzędzi
- `docs/sprint3-backlog.md` — bieżący stan sprintu
- `adr/ADR-006.md` do `adr/ADR-017.md` — formalne ADR-y (ADR-001 do 005 tylko w Doc4)

Pierwszeństwo dla bieżącego stanu: pliki .md w repo. Pierwszeństwo dla formalnych decyzji: dokumenty docx.

---

## Aktywne długi techniczne (krytyczne i wysokie)

### 🔴 Krytyczne
- **DŁUG-309** — Pełna pozycja finansowa w ExposureLedger (BLOKUJE PoC JForex)

### 🟠 Wysokie
- **DŁUG-311** — RiskCoordinatorTailer offset (w trakcie Paczka 3C)
- **DŁUG-313** — Rename timestampMono → sequenceNumber (Paczka 3D, hard dep po 3C)
- **DŁUG-314** — Reconnect/reconcile w BrokerSession (ADR-003, NOWY)

Pełna lista: `Doc6 v1.1` + `docs/sprint3-backlog.md`.

---

## Otwarte kwestie architektoniczne

Spory z 18.04.2026, nierozstrzygnięte:

1. **Disruptor+CQ vs PostgreSQL log** — WYSOKI priorytet, blokuje Etap 4
2. **Opcje B/C dla adaptera JForex** — ŚREDNI priorytet, przed Etapem 2

Wymagają dedykowanej sesji Gemini+GPT+Claude. Szczegóły: `Doc3 Uzupełnienie v2.2`.

---

## Zasady współpracy (skrót)

Pełne 29 zasad w `docs/ZASADY_WSPOLPRACY.md` (v2.0). Kluczowe:

- Komunikacja w języku polskim, po ludzku
- Hierarchia konfliktów: Gemini → GPT → Claude → Architekt
- Bez terminów i harmonogramów (zasada 5.11)
- Commit tagi `[ADR-X]`
- Safe Mode Code — przed destrukcyjnymi operacjami konfiguruj alternatywę
- QG self-discipline — Claude nie rozstrzyga unilateralnie
- Bez imienia Architekta — zawsze "Architekt"
- Rytm pracy Architekta — Claude nie sugeruje zmęczenia ani nie proponuje zakończenia dnia

---

## Szybki start dla nowego agenta

Jeśli jesteś nowym agentem AI (GPT, Gemini, Codex) dołączonym do projektu:

1. Przeczytaj `docs/ZASADY_WSPOLPRACY.md` (29 zasad).
2. Przeczytaj `docs/ZESPOL.md` (twoja rola i ograniczenia).
3. Przeczytaj `docs/STAN_IMPLEMENTACJI.md` (stan kodu).
4. Przeczytaj `docs/sprint3-backlog.md` (co jest w trakcie).
5. Jeśli robisz coś architektonicznego — Doc3A+3B v2.1 + Doc3 Uzupełnienie v2.2.
6. Jeśli robisz coś związanego z ADR — Doc4 v2.0 lub `adr/`.
7. Jeśli pracujesz nad paczką Sprint — dedykowany prompt od Claude z kontekstem.

---

**Dokument kanoniczny:** `Doc7 — Stan Implementacji v1.0` (szczegóły techniczne)
**Wersja:** 2.1
**Data:** 22.04.2026
