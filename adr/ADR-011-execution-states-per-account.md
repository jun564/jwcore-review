# ADR-011 — Tryby awaryjne SAFE/HALT/KILL per rachunek brokerski

**Data:** 18.04.2026
**Status:** Zatwierdzony po poprawkach (Gemini + GPT + Claude + Architekt)
**Kontekst:** Doc2 decyzja 3.2 (zmieniona 18.04.2026) wprowadza dwa rachunki brokerskie w Dukascopy — krypto i forex/surowce. Doc3A ADR-005 definiował tryby awaryjne (SAFE/HALT/KILL) zakładając jeden rachunek. Architektura dwurachunkowa wymaga dookreślenia.

**To jest blocker Sprintu 3** — Execution Adapter nie wie jak reagować bez tej decyzji.

## Decyzja

### 1. Dwa niezależne procesy Execution Adapter

Każdy rachunek obsługiwany przez osobny proces JVM:
- `jwcore-execution-crypto` — sesja JForex krypto
- `jwcore-execution-forex` — sesja JForex forex/surowce

**Osobne:** systemd unity, logi, pliki state, porty zarządzania, pliki konfiguracyjne.

### 2. Tryby awaryjne per rachunek (poprawiona semantyka GPT #1)

Każdy proces Execution ma własny stan awaryjny. **Cztery stany (nie trzy):**

- **RUN** — normalna praca, zlecenia wysyłane, pozycje zarządzane
- **SAFE** — brak nowych zleceń, istniejące pozycje monitorowane, system żyje i raportuje
- **HALT** — twarde zatrzymanie handlu, pozycje zamrożone (zero interakcji z brokerem poza reconnect), proces żyje
- **KILL** — zakończenie procesu (pozycje zostają na brokerze)

### 3. Hierarchia stanów (dopisek GPT)

**"Bardziej restrykcyjny wygrywa":**

```
KILL > HALT > SAFE > RUN
```

**Konflikt równoczesnych decyzji:**
- Lokalny Execution chce SAFE, globalny Risk Coordinator emituje HALT → wygrywa HALT
- Dwa źródła decyzji emitują eventy równocześnie — Execution wybiera najbardziej restrykcyjny
- Execution w każdym cyklu głównym:
  1. Czyta wszystkie oczekujące `RiskDecisionEvent` z CQ
  2. Pobiera lokalne decyzje ryzyka (Poziom 1-2)
  3. ExecutionStateResolver wybiera najbardziej restrykcyjny stan
  4. Stosuje stan

**Stan nigdy nie degraduje się automatycznie** — powrót z HALT do RUN wymaga:
- Explicitnego `RiskDecisionEvent` z `desired_state: RUN` od Risk Coordinatora, LUB
- Decyzji operatora (Architekta) przez interfejs komendy

### 4. Przejścia między trybami (dozwolone)

```
RUN → SAFE       (soft stop, np. koniec sesji)
RUN → HALT       (twardy stop, problem wykryty)
SAFE → RUN       (wznowienie po review)
SAFE → HALT      (eskalacja)
HALT → SAFE      (po rozwiązaniu problemu, explicit decision)
HALT → KILL      (decyzja operacyjna, np. restart)
KILL → (brak)    (terminal — tylko restart procesu)

Po restarcie: proces wraca do RUN (domyślnie) lub ostatniego zapamiętanego stanu (opcjonalnie w future)
```

### 5. Scope operacji awaryjnych

- **HALT per proces** — tylko jeden rachunek wstrzymuje nowe zlecenia
- **HALT globalny** — oba procesy wstrzymane (decyzja operatora lub globalny Risk Coordinator na podstawie cross-account)
- **KILL per proces** — jeden proces wyłączony, drugi działa dalej
- **KILL globalny** — oba procesy wyłączone

### 6. Brak automatycznej likwidacji pozycji

Zgodnie z decyzją Architekta: przy KILL **nie zamykamy pozycji sami**. Broker Dukascopy likwiduje pozycje po kolei przez mechanizm stop-out gdy margin spadnie.

**Uzasadnienie biznesowe:** masowe zamknięcie przy flash crash = najgorszy moment do egzekucji. Broker likwiduje po jednej pozycji w swoim tempie, my tracimy mniej na spread + slippage.

### 7. Reconnect per broker session

Utrata połączenia z jednym rachunkiem **nie wymusza** działania na drugim. Reconnect logic wykonuje się niezależnie w każdym procesie Execution.

### 8. Risk Engine — architektura dwupoziomowa (poprawka GPT #2)

**Lokalne Risk Engine (w każdym Execution, Poziom 1-2):**
- Margin monitoring lokalny (emisja MarginUpdateEvent)
- Limit liczby otwartych pozycji per rachunek
- Limit frequency (X zleceń / minutę)
- Przy przekroczeniu → lokalny stan SAFE + emisja alertu

**Global Risk Coordinator (osobny proces JVM, Poziom 3) — NOWY:**
- `jwcore-risk-coordinator` — trzeci proces w topologii (po execution-crypto, execution-forex)
- Tailuje events-business i market-data z obu rachunków
- Oblicza metryki cross-account: total exposure, korelacje, portfolio drawdown
- Emituje `RiskDecisionEvent` gdy przekroczono limity cross-account
- **NIE jest state rebuilderem** (ADR-008) — konsument stanu przez StateRebuiltEvent
- Utrzymuje **własny stan ryzyka** (ekspozycja portfela), nie stan pozycji

### 9. Komunikacja między procesami

Procesy Execution **nie komunikują się bezpośrednio**. Komunikacja przez Chronicle Queue:
- Oba zapisują do wspólnej kolejki `events-business`
- Oba czytają z wspólnej kolejki `market-data`
- Risk Coordinator tailuje oba i emituje `RiskDecisionEvent`

## Konsekwencje

**Pozytywne:**
- Izolacja awarii — crash jednego procesu nie wpływa na drugi
- Niezależny restart i deploy per rachunek
- Czystsze logi diagnostyczne
- Risk widzi oba rachunki przez event sourcing, nie przez shared state
- Decyzje cross-account zcentralizowane w Risk Coordinator
- Zgodność z Event Sourcing pure

**Negatywne:**
- Wyższy koszt RAM (~4 GB zamiast 1.5 GB — trzy procesy JVM zamiast jednego)
- Trzy unity systemd do zarządzania
- Konfiguracja per-proces (credentials, limity) — trzeba konwencji nazewniczej
- Większa złożoność koordynacji (resolver stanów, hierarchia)

**Mitygacja:**
- 12 GB RAM na Infomaniak → zapas wystarczający (~5 GB wolne po wszystkich procesach)
- Konfiguracja w plikach per-proces: `execution-crypto.properties`, `execution-forex.properties`, `risk-coordinator.properties`
- ExecutionStateResolver — dedykowana klasa, testowana unit testami (wszystkie permutacje)

## Zależności

- ADR-005 (tryby awaryjne — baza)
- ADR-008 (sole state rebuilder — Risk Coordinator nie rebuilduje)
- Doc2 decyzja 3.2 (dwa rachunki brokerskie)
- Sprint 3 — implementacja
