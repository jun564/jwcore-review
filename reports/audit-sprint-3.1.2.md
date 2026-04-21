# Sprint 3.2 / Paczka 1 — Audyt architektoniczny Sprint 3.1.2

## Kontekst

Zakres audytu objął moduły:
- `jwcore-domain`
- `jwcore-execution-common`
- `jwcore-execution-crypto`
- `jwcore-execution-forex`
- `jwcore-risk-coordinator`

Audyt koncentruje się na ryzykach wprowadzonych po zmianach Sprint 3.1.2 (fazy intentów, podwójne timeouty, pipeline `RiskDecisionEvent`, bounded tailer, nowe eventy lifecycle i błędów przetwarzania).

---

## Znaleziska KRYTYCZNE

### KRYT-001 — `IntentRegistry.bind()` resetuje fazę i `submittedAt` przy duplikacie/rebindzie
- **Opis:** `bind()` zawsze nadpisuje fazę na `PENDING_SUBMIT` i czyści `submittedAtByIntentId`. Jeśli ten sam `intentId` pojawi się ponownie (duplikat envelope, replay, rebinding po restarcie), możliwa jest degradacja z `SUBMITTED` do `PENDING_SUBMIT`, co może wygenerować fałszywy `OrderTimeoutEvent` (execution timeout) dla już wysłanego zlecenia.
- **Plik+linia:** `jwcore-execution-common/src/main/java/org/jwcore/execution/common/registry/IntentRegistry.java:36-42`.
- **Proponowane rozwiązanie (konkret):**
  1. W `bind(...)` dodać guard: jeśli `intentPhases.get(intentId) == SUBMITTED || intentPhases.get(intentId) == TERMINATED`, **nie resetować** fazy i **nie usuwać** `submittedAtByIntentId`.
  2. Dla rebindu tego samego `intentId` i tego samego `canonicalId` aktualizować wyłącznie mapowanie `byCanonicalId` (jeśli potrzeba), bez zmiany fazy.
  3. Dodać test regresyjny: duplikat `OrderIntentEvent` po `OrderSubmittedEvent` nie może przełączyć fazy na `PENDING_SUBMIT`.
- **Priorytet:** Krytyczny.

### KRYT-002 — Bounded `RiskCoordinatorTailer` gubi historyczne `OrderSubmittedEvent`, a engine liczy ekspozycję tylko z aktualnego okna
- **Opis:** tailer trzyma wyłącznie ostatnie `receivedCapacity` eventów (domyślnie 10000), a `RiskCoordinatorEngine.evaluate(...)` na każdym cyklu przelicza ekspozycję tylko z tej przyciętej listy. Po przekroczeniu capacity starsze ekspozycje „znikają”, co może obniżyć decyzję z `HALT/SAFE` do `RUN` mimo braku realnego zamknięcia pozycji.
- **Plik+linia:**
  - `jwcore-risk-coordinator/src/main/java/org/jwcore/riskcoordinator/tailer/RiskCoordinatorTailer.java:31-35,48-51,67-70`.
  - `jwcore-risk-coordinator/src/main/java/org/jwcore/riskcoordinator/engine/RiskCoordinatorEngine.java:33-47`.
  - `jwcore-risk-coordinator/src/main/java/org/jwcore/riskcoordinator/app/Main.java:82-84`.
- **Proponowane rozwiązanie (konkret):**
  1. Zmienić kontrakt tailera: zamiast „snapshot całej bounded listy” dostarczać **inkrementalne** eventy od ostatniego offsetu.
  2. W `RiskCoordinatorEngine` utrzymywać stan per account (aktywny exposure ledger), aktualizowany eventami lifecycle (co najmniej `OrderSubmittedEvent` plus finalizatory).
  3. Do czasu pełnego lifecycle (Paczki 3-4 Sprint 3.2) dodać mechanizm „nie obniżaj stanu ryzyka bez dowodu zamknięcia”, aby uniknąć fałszywego `RUN` po evikcji.
- **Priorytet:** Krytyczny.

### KRYT-003 — `OrderUnknownEvent(BROKER_TIMEOUT)` nie wpływa na RiskCoordinator (brak „freeze account”)
- **Opis:** engine ignoruje wszystko poza `OrderSubmittedEvent`. Oznacza to, że timeout brokera (stan nieznany) nie podnosi restrykcji ryzyka i nie uruchamia ochrony konta.
- **Plik+linia:**
  - `jwcore-risk-coordinator/src/main/java/org/jwcore/riskcoordinator/engine/RiskCoordinatorEngine.java:35-37`.
  - `jwcore-execution-common/src/main/java/org/jwcore/execution/common/registry/IntentRegistry.java:103-105` (emisja `OrderUnknownEvent` dla `BROKER_TIMEOUT`).
- **Proponowane rozwiązanie (konkret):**
  1. Rozszerzyć `RiskCoordinatorEngine.evaluate(...)`, aby konsumował `OrderUnknownEvent` z `reason=BROKER_TIMEOUT` i oznaczał account jako minimum `SAFE` (lub `HALT` wg ADR operacyjnego).
  2. Rozszerzyć payload/parse o `accountId` także dla `OrderUnknownEvent` (albo odczytywać z `canonicalId` jeśli to wystarczy biznesowo).
  3. Dodać test: po `OrderUnknownEvent(BROKER_TIMEOUT)` `RiskDecisionEmitter` emituje decyzję ograniczającą.
- **Priorytet:** Krytyczny.

---

## Znaleziska WYSOKIE

### WYS-001 — Wyścig graniczny przy timeoutach: możliwe `OrderTimeoutEvent` mimo submitu tuż „na granicy”
- **Opis:** kolejność w cyklu to `absorb(freshEvents)` -> `processOrderIntents(...)` -> `checkTimeouts(...)`. Jeżeli `OrderSubmittedEvent` zostanie opóźniony poza bieżący `freshEvents` lub dojdzie do ponownego związania intentu, `PENDING_SUBMIT` może przekroczyć execution timeout i zostać zakończony tuż przed widocznością submitu. Skutek: semantycznie sprzeczne zdarzenia (`OrderTimeoutEvent` i późny submit/filled).
- **Plik+linia:**
  - `jwcore-execution-crypto/src/main/java/org/jwcore/execution/crypto/runtime/ExecutionRuntime.java:75-79,130-134`.
  - `jwcore-execution-common/src/main/java/org/jwcore/execution/common/registry/IntentRegistry.java:71-83,88-96,134-153`.
- **Proponowane rozwiązanie (konkret):**
  1. W `checkTimeouts` dodać warunek bezpieczeństwa: przed emisją `OrderTimeoutEvent` ponownie zweryfikować, czy faza nadal `PENDING_SUBMIT` i czy nie istnieje `submittedAt`.
  2. Dodać monotoniczny „watermark event processing” (lub min. kolejkę deterministyczną), aby timeout nie wyprzedzał eventów lifecycle z tego samego intentu.
  3. Dodać test graniczny z tym samym timestampem dla submit/timeout.
- **Priorytet:** Wysoki.

### WYS-002 — Brak walidacji `nodeId.isBlank()` w execution (ADR-015: ryzyko pustego `sourceProcessId` z konfiguracji)
- **Opis:** `ExecutionPropertiesLoader` wymaga `nodeId != null`, ale nie odrzuca blank. Potem `EventEmitter` odrzuci blank dopiero w runtime (wyjątek konstruktora), a nie na etapie ładowania konfiguracji.
- **Plik+linia:**
  - `jwcore-execution-crypto/src/main/java/org/jwcore/execution/crypto/config/ExecutionPropertiesLoader.java:23`.
  - `jwcore-execution-forex/src/main/java/org/jwcore/execution/forex/config/ExecutionPropertiesLoader.java:23`.
  - `jwcore-execution-common/src/main/java/org/jwcore/execution/common/emit/EventEmitter.java:40-43`.
- **Proponowane rozwiązanie (konkret):**
  1. W obu loaderach po odczycie `nodeId` dodać `if (nodeId.isBlank()) throw ...` (analogicznie jak w RiskCoordinator loader).
  2. Dodać testy properties loaderów dla pustego `nodeId`.
- **Priorytet:** Wysoki.

### WYS-003 — `markTerminal(...)` w runtime usuwa intent bez oznaczenia jako terminated
- **Opis:** publiczne `markTerminal(UUID)` wywołuje `intentRegistry.remove(intentId)`, a nie `markTerminated(intentId)`. Przy użyciu tej metody możliwa jest ponowna akceptacja duplikatu (`isTerminated == false`).
- **Plik+linia:**
  - `jwcore-execution-crypto/src/main/java/org/jwcore/execution/crypto/runtime/ExecutionRuntime.java:190-192`.
  - `jwcore-execution-forex/src/main/java/org/jwcore/execution/forex/runtime/ExecutionRuntime.java:190-192`.
  - `jwcore-execution-common/src/main/java/org/jwcore/execution/common/registry/IntentRegistry.java:155-166`.
- **Proponowane rozwiązanie (konkret):**
  1. Zmienić implementację `markTerminal(...)` na `intentRegistry.markTerminated(intentId)`.
  2. Dodać test kontraktowy: po `markTerminal` duplikat `OrderIntentEvent` nie jest przetwarzany.
- **Priorytet:** Wysoki.

---

## Znaleziska ŚREDNIE

### SRD-001 — `submittedAtByIntentId` nie ma testów spójności z fazą (ryzyko „silent drift”)
- **Opis:** implementacja zakłada spójność: `SUBMITTED` <=> wpis w `submittedAtByIntentId`. Dziś brak testu regresyjnego, który łapie niespójność po sekwencjach `bind -> markSubmitted -> absorb(terminal) -> rebind`.
- **Plik+linia:** `jwcore-execution-common/src/main/java/org/jwcore/execution/common/registry/IntentRegistry.java:27,40-42,81-83,151-152,58-60`.
- **Proponowane rozwiązanie (konkret):**
  1. Dodać test biało-skrzynkowy przez API (`getPhase`, `checkTimeouts`) z sekwencją duplikatów/rebindów.
  2. Rozważyć jawny invariant check (metoda wewnętrzna uruchamiana w testach): gdy faza `SUBMITTED`, `submittedAt` musi istnieć; gdy `PENDING_SUBMIT`, `submittedAt` nie może istnieć.
- **Priorytet:** Średni.

### SRD-002 — `RiskDecisionEmitter` po restarcie zawsze emituje „pierwszy stan” (edge-trigger + puste `previous`)
- **Opis:** mapa `previous` startuje pusta, więc pierwszy cykl po restarcie emituje decyzje dla wszystkich kont niezależnie od realnej zmiany względem stanu sprzed restartu. To może powodować burst eventów i zbędne reakcje downstream.
- **Plik+linia:** `jwcore-risk-coordinator/src/main/java/org/jwcore/riskcoordinator/emitter/RiskDecisionEmitter.java:15,30-42`.
- **Proponowane rozwiązanie (konkret):**
  1. Dodać opcjonalne odtworzenie `previous` z ostatnich `RiskDecisionEvent` przy starcie.
  2. Alternatywnie: wprowadzić tryb „warm start”, który pierwszą iterację tylko inicjalizuje `previous` bez emisji.
  3. Dodać test restartowy (re-inicjalizacja emitera + brak sztucznej emisji przy niezmienionym stanie).
- **Priorytet:** Średni.

### SRD-003 — `parseExposure` może generować flood warningów przy malformed eventach
- **Opis:** każde uszkodzone `OrderSubmittedEvent` generuje `LOGGER.warning(...)`; brak limitowania/log samplingu. W scenariuszu ataku lub błędu upstream grozi to degradacją I/O i zaciemnieniem logów.
- **Plik+linia:** `jwcore-risk-coordinator/src/main/java/org/jwcore/riskcoordinator/engine/RiskCoordinatorEngine.java:69-77`.
- **Proponowane rozwiązanie (konkret):**
  1. Wprowadzić rate-limiter logów (np. max N ostrzeżeń / okno czasowe / typ błędu).
  2. Agregować licznik odrzuconych malformed eventów i emitować telemetryczny event health.
  3. Dodać test obciążeniowy logiki parse (bez asercji na logger, z asercją liczników).
- **Priorytet:** Średni.

### SRD-004 — Luka semantyczna lifecycle: brak aktywnego mechanizmu zamknięcia SUBMITTED poza timeoutem brokera
- **Opis:** dopóki nie ma pełnego pipeline (`OrderFilledEvent`/`OrderCanceledEvent` jako produkt execution/broker adapter), intent może pozostawać długo w `SUBMITTED`; obecnie praktyczny „safety net” to `OrderUnknownEvent` po broker timeout.
- **Plik+linia:**
  - `jwcore-execution-common/src/main/java/org/jwcore/execution/common/registry/IntentRegistry.java:80-85,98-105,173-181`.
  - `jwcore-execution-crypto/src/main/java/org/jwcore/execution/crypto/runtime/ExecutionRuntime.java:130-134`.
- **Proponowane rozwiązanie (konkret):**
  1. W Paczkach 3-4 Sprint 3.2 domknąć lifecycle eventami fill/cancel/reject z brokera.
  2. Do czasu wdrożenia dodać wskaźnik „stale submitted intents” i alarm po SLA.
- **Priorytet:** Średni.

---

## Znaleziska NISKIE

### NIS-001 — Część testów jakościowo słaba (assert „brak wyjątku” zamiast kontraktu)
- **Opis:** występują testy typu `assertDoesNotThrow` bez mocnych asercji semantycznych.
- **Plik+linia:** `jwcore-domain/src/test/java/org/jwcore/domain/DomainValidationTest.java:19,42`.
- **Proponowane rozwiązanie (konkret):**
  1. Zamienić `assertDoesNotThrow` na asercje właściwości obiektu (np. znormalizowane pola, invariants).
  2. Dodać weryfikację komunikatów walidacyjnych w testach negatywnych.
- **Priorytet:** Niski.

### NIS-002 — Test kontraktu enum oparty o długość `values()` (kruchy wzorzec)
- **Opis:** test sprawdza `Timeframe.values().length >= 6`, co nie weryfikuje kontraktu biznesowego i jest podatne na przypadkowe przejścia.
- **Plik+linia:** `jwcore-domain/src/test/java/org/jwcore/domain/DomainValidationTest.java:121`.
- **Proponowane rozwiązanie (konkret):**
  1. Zastąpić liczebność explicite wymaganym zbiorem nazw (`Set.of("TICK", "M1", ... )`) i asercją `containsAll`.
- **Priorytet:** Niski.

### NIS-003 — Backward-compatible konstruktor `EventEnvelope` nadal wspiera `sourceProcessId="unknown"`
- **Opis:** utrzymanie kompatybilności jest zrozumiałe, ale w nowym przepływie ADR-015 to potencjalny sygnał brakującej konfiguracji.
- **Plik+linia:** `jwcore-domain/src/main/java/org/jwcore/domain/EventEnvelope.java:31-44`.
- **Proponowane rozwiązanie (konkret):**
  1. Dodać telemetryczny licznik eventów z `sourceProcessId="unknown"` i fail-fast w aplikacjach produkcyjnych (feature flag).
- **Priorytet:** Niski.

---

## Długi techniczne

### DŁUG-301 — Rebind-safe `IntentRegistry`
- **Zakres:** odporność `bind()` na duplikaty/replay bez resetu fazy `SUBMITTED`.
- **Powiązane znaleziska:** KRYT-001, SRD-001.

### DŁUG-302 — Incrementalny tailing + trwały ledger ekspozycji w RiskCoordinator
- **Zakres:** odejście od snapshotu bounded listy na rzecz przetwarzania inkrementalnego i stanu trwałego.
- **Powiązane znaleziska:** KRYT-002.

### DŁUG-303 — Polityka reakcji na `OrderUnknownEvent(BROKER_TIMEOUT)`
- **Zakres:** formalna reguła „freeze/degrade account” + implementacja w engine + testy e2e.
- **Powiązane znaleziska:** KRYT-003, SRD-004.

### DŁUG-304 — Deterministyczna obsługa wyścigów timeout/submit
- **Zakres:** watermark/ordering barrier + testy graniczne czasu.
- **Powiązane znaleziska:** WYS-001.

### DŁUG-305 — Spójność ADR-015 (`sourceProcessId`) na etapie konfiguracji
- **Zakres:** walidacja `nodeId` (non-blank) w execution loaderach + testy.
- **Powiązane znaleziska:** WYS-002, NIS-003.

### DŁUG-306 — Restart-safe `RiskDecisionEmitter`
- **Zakres:** odtworzenie `previous` lub warm start bez burstu emisji.
- **Powiązane znaleziska:** SRD-002.

### DŁUG-307 — Hardening parsera ekspozycji i higiena logów
- **Zakres:** rate-limit ostrzeżeń, metryki malformed events, testy obciążeniowe.
- **Powiązane znaleziska:** SRD-003.

### DŁUG-308 — Jakościowe testy kontraktowe zamiast asercji „brak wyjątku” i długości enumów
- **Zakres:** refaktoryzacja wybranych testów domain pod kontrakty semantyczne.
- **Powiązane znaleziska:** NIS-001, NIS-002.

---

## Podsumowanie

- **Liczba znalezisk:**
  - Krytyczne: **3**
  - Wysokie: **3**
  - Średnie: **4**
  - Niskie: **3**

- **Najważniejsze ryzyka do Paczki 2 (natychmiast):**
  1. Uszczelnić `IntentRegistry.bind()` przed resetem fazy (KRYT-001).
  2. Zmienić model danych RiskCoordinator (KRYT-002).
  3. Włączyć `OrderUnknownEvent(BROKER_TIMEOUT)` do polityki ryzyka (KRYT-003).

- **Rekomendacje dla Paczek 2-5 Sprint 3.2:**
  - **Paczka 2:** KRYT-001 + WYS-001 + WYS-002.
  - **Paczka 3:** KRYT-003 + SRD-004 (domknięcie lifecycle eventami brokera).
  - **Paczka 4:** KRYT-002 + SRD-002 (incremental engine + restart safety).
  - **Paczka 5:** SRD-003 + NIS-001/NIS-002 (hardening parsera i testów jakościowych).
