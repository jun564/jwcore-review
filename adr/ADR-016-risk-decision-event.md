# ADR-016: Risk Decision Event i ExposureLedger w risk-coordinator

## Status
Accepted

## Kontekst
W Sprint 3.1.2 audyt wykazał dwa krytyczne problemy:
- **KRYT-002**: bounded `LinkedHashMap` w tailerze (`removeEldestEntry`) gubi historyczne eventy, więc agregacja ekspozycji operuje na niepełnych danych.
- **KRYT-003**: silnik ryzyka uwzględniał tylko `OrderSubmittedEvent`, ignorując `OrderFilledEvent`, `OrderCanceledEvent`, `OrderRejectedEvent` i `OrderUnknownEvent`.

Risk-coordinator realizuje globalną politykę ryzyka per `accountId`, a nie per pojedynczy intent. Dotychczasowa ścieżka łączyła logikę decyzyjną i emisję eventów, utrudniając edge-triggered publikację i deterministyczny rebuild stanu.

Dodatkowo Sprint 3.2 Paczka 3A dostarczyła eventy `OrderFilledEvent`, `OrderCanceledEvent` i rozszerzone `OrderRejectedEvent`/`OrderUnknownEvent` z `accountId`, co umożliwia przejście na inkrementalne przetwarzanie per event.

## Decyzja
1. **Model decyzyjny i emisja**
   - `RiskCoordinatorEngine` działa jako czysta logika domenowa: `apply(event)` aktualizuje projekcje i zwraca dotknięte konta, a `evaluateAndBuildIfChanged(accountId)` zwraca `RiskDecisionEvent` tylko przy zmianie stanu.
   - Emisja jest edge-triggered i wykonywana przez `EventEmitter` w `Main`, bez `RiskDecisionEmitter`.

2. **ExposureLedger jako projekcja in-memory**
   - Wprowadzamy `ExposureLedger` jako osobną klasę domenową.
   - Ledger śledzi **execution exposure (intent exposure)**, a nie pełną ekspozycję pozycyjną rachunku.
   - Invariant: `exposureByAccount >= BigDecimal.ZERO` (clamp do zera przy próbie zejścia poniżej zera).

3. **Rebuild + tailing z checkpointem**
   - Start modułu: blokujący catch-up (`rebuild`) z journalu do EOF.
   - Po catch-up wykonywany jest initial publish aktualnego stanu monitorowanych kont.
   - Następnie działa inkrementalny `pollSince` od zapamiętanego checkpointu, bez duplikacji eventów już przetworzonych.

4. **Semantyka `OrderUnknownEvent`**
   - `OrderUnknownEvent` degraduje konto do `SAFE`.
   - `OrderUnknownEvent` **nie zmienia** wartości ekspozycji w ledgerze.

5. **Reset stanu SAFE -> RUN (MVP)**
   - W Paczce 3B reset realizowany jest przez restart modułu i rebuild z journalu.
   - Brak komendy runtime do ręcznego resetu.

## Konsekwencje
- Eliminujemy utratę historii przez bounded bufor i odseparowujemy logikę decyzji od infrastruktury emisji.
- Stabilizujemy semantykę edge-triggered: `RiskDecisionEvent` jest publikowany wyłącznie przy zmianie stanu.
- `OrderRejectedEvent` w MVP nie rozlicza ekspozycji (brak `size` w payloadzie); to świadome ograniczenie do czasu dodania lookup `intentId -> submitted size`.
- Wymagana jawna konfiguracja `monitoredAccounts`; brak konfiguracji zatrzymuje start modułu (fail-fast).

### Długi techniczne (kolejne paczki)
- eskalacja `SAFE -> HALT`,
- panel operatorski,
- powiadomienia Telegram,
- `RiskStateResetCommand`,
- integracja z Chronicle Queue,
- pełny model portfelowy (DŁUG-309),
- pełne rozliczanie `OrderRejectedEvent` przez mapę `intentId -> size` (DŁUG-310).
