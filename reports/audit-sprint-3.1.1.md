# Audit architektoniczny Sprint 3.1.1 (Paczka 3.5)

Data: 2026-04-19  
Zakres: `jwcore-execution-common`, `jwcore-execution-crypto`, `jwcore-execution-forex`, `jwcore-risk-coordinator`, oraz elementy domeny ze Sprint 3.1.1 (`EventEnvelope`, `EventType`, `RejectReason`, `OrderRejectedEvent`).

---

## 1) Wykryte bugi

### KRYTYCZNY

1. **Risk Coordinator nie emituje `RiskDecisionEvent` (brak faktycznej pętli decyzyjnej).**  
   **Lokalizacja:** `jwcore-risk-coordinator/src/main/java/.../app/Main.java`, `.../engine/RiskCoordinatorEngine.java`, `.../tailer/RiskCoordinatorTailer.java`  
   **Opis:** moduł tailuje eventy i liczy placeholder ekspozycji (`received().size()`), ale nigdzie nie tworzy ani nie publikuje `RiskDecisionEvent`. W praktyce globalny risk nie steruje runtime execution.  
   **Kierunek:** dodać loop: agregacja ekspozycji z eventów biznesowych -> ocena przez `RiskCoordinatorEngine` -> emisja `RiskDecisionEvent` przez `EventEmitter` do journalu biznesowego.

2. **Ryzyko awarii cyklu runtime na pojedynczym złym evencie `OrderIntentEvent` (brak izolacji błędu na poziomie eventu).**  
   **Lokalizacja:** `jwcore-execution-crypto/.../ExecutionRuntime.java`, `jwcore-execution-forex/.../ExecutionRuntime.java` (`processOrderIntents`, `UUID.fromString(localIntentId)`, `parseOrderIntent`)  
   **Opis:** wyjątek formatowania `localIntentId` lub payload (`split`/`Double.parseDouble`) przerywa cały `tickCycle()`. Jeden uszkodzony event może zatrzymać przetwarzanie kolejnych eventów i timeoutów.  
   **Kierunek:** fail-safe per event (np. izolacja wyjątku na iteracji, emit diagnostycznego eventu błędu, kontynuacja pętli).

### WYSOKI

3. **Nieskończony wzrost bufora `received` w `RiskCoordinatorTailer` (memory leak).**  
   **Lokalizacja:** `jwcore-risk-coordinator/.../RiskCoordinatorTailer.java`  
   **Opis:** lista `received` rośnie bez limitu i bez strategii kompaktowania/okna czasowego. Długie działanie procesu grozi wzrostem pamięci i GC pressure.  
   **Kierunek:** bounded buffer / okno czasowe / agregacja strumieniowa bez trwałego trzymania wszystkich envelope.

4. **Entry-pointy execution/risk uruchamiane na `InMemoryEventJournal` i `StubBrokerSession` (ryzyko niezamierzonego użycia pseudo-runtime).**  
   **Lokalizacja:** `jwcore-execution-crypto/.../app/Main.java`, `jwcore-execution-forex/.../app/Main.java`, `jwcore-risk-coordinator/.../app/Main.java`  
   **Opis:** `main()` bootstrapuje komponenty testowo-symulacyjne jako domyślne. To może maskować błędy środowiskowe i łamać oczekiwania produkcyjne.  
   **Kierunek:** osobny profil DEV/TEST vs PROD, wyraźna separacja adapterów CQ i brokera produkcyjnego.

5. **Niedomknięta kardynalność terminal eventów względem ADR-015.**  
   **Lokalizacja:** `jwcore-domain/.../EventType.java`, `jwcore-execution-common/.../IntentRegistry.java`  
   **Opis:** ADR-015 wskazuje kardynalność wokół `OrderSubmittedEvent`/`OrderRejectedEvent`/`OrderTimeoutEvent`, a w kodzie brak `OrderSubmittedEvent` w `EventType`, przez co model lifecycle nie jest kompletny i może wymuszać obejścia.  
   **Kierunek:** ujednolicić kontrakt domenowy (typy eventów + absorber terminali + testy kardynalności).

### ŚREDNI

6. **`markTerminal(UUID intentId)` w runtime nie aktualizuje tombstone po `correlationId`.**  
   **Lokalizacja:** `ExecutionRuntime` crypto/forex (`markTerminal`)  
   **Opis:** metoda usuwa tylko pending intent; deduplikacja bazuje na `correlationId` i tombstone z `IntentRegistry`. Przy niektórych ścieżkach zewnętrznych (poza `absorb`) może powstać niespójność semantyczna „terminal".  
   **Kierunek:** ustalić jedną ścieżkę terminalizacji (event-driven + absorb) albo rozszerzyć API tak, by `markTerminal` działał zgodnie z kluczem deduplikacji.

7. **Domyślna korelacja w `EventEmitter.createEnvelope(...)` = `eventId` może być myląca dla zdarzeń reaktywnych.**  
   **Lokalizacja:** `jwcore-execution-common/.../emit/EventEmitter.java`  
   **Opis:** przeciążenie bez jawnego `correlationId` tworzy nową korelację zamiast dziedziczyć istniejącą; łatwo o regresję ADR-015 przy przyszłych eventach pochodnych.  
   **Kierunek:** ograniczyć użycie przeciążenia „bez correlationId" do eventów systemowych, a dla pochodnych wymusić jawne przekazanie korelacji.

### NISKI

8. **Brak walidacji „not blank" dla `nodeId` na poziomie `ExecutionRuntimeConfig`.**  
   **Lokalizacja:** `.../ExecutionRuntimeConfig.java` (crypto/forex)  
   **Opis:** `nodeId` sprawdzany na `null`, ale blank wybucha dopiero później w `EventEmitter`.  
   **Kierunek:** fail-fast już w config record (`isBlank`).

---

## 2) Wykryte długi techniczne (do sprint3-backlog)

1. **Placeholder logiki ekspozycji w risk coordinator** (`tailer.received().size()`) zamiast modelu portfelowego.  
   **Wpływ:** bardzo wysoki — blokuje realne decyzje cross-account w Sprint 3.2/4.

2. **Brak strategii retencji i snapshotów stanu risk-coordinator.**  
   **Wpływ:** wysoki — brak kontrolowanego zużycia pamięci i słaba odtwarzalność stanu po restarcie.

3. **Silna duplikacja kodu crypto/forex (runtime, config loader, main, testy).**  
   **Wpływ:** średni/wysoki — poprawki bezpieczeństwa/ADR trzeba wdrażać podwójnie (ryzyko dryfu).

4. **Model eventów terminalnych nie domknięty kontraktowo (`OrderSubmittedEvent` brak w enum).**  
   **Wpływ:** średni — ryzyko niespójnej implementacji lifecycle w kolejnych sprintach.

5. **Testy „szczęśliwej ścieżki" dominują nad testami odporności na błędne payloady/formaty UUID.**  
   **Wpływ:** średni — regresje jakości danych ujawniają się późno (integracja/staging).

---

## 3) Naruszenia ADR

### ADR-011 (Execution states per account)
- **Częściowa zgodność:** stany `RUN/SAFE/HALT/KILL` istnieją i resolver restrykcyjności działa.  
- **Ryzyko naruszenia operacyjnego:** brak pełnej ścieżki globalnego sterowania przez risk-coordinator (brak emisji `RiskDecisionEvent`).

### ADR-012 (Chronicle Queue pinning)
- **Konfiguracja wersji** CQ jest pinned w parent POM (`5.25ea16`) — zgodne.  
- **Luka operacyjna:** entry-pointy modułów nie używają adaptera CQ, tylko in-memory (brak realnego pinning-path runtime w `main()`).

### ADR-013 (Mandatory JVM flags)
- **Poziom build/test:** flagi `--add-opens` są wymuszone globalnie w parent POM (Surefire/Failsafe) — zgodne.  
- **Poziom runtime/prod:** brak dowodów egzekucji w artefaktach modułów (brak unit/systemd w repo tych modułów) — ryzyko operacyjne poza kodem Javy.

### ADR-015 (Correlation & Provenance)
- **Postęp:** `sourceProcessId` i `correlationId` obecne; fail-fast dla `OrderIntentEvent` dodany; tombstone działa po `correlationId`.  
- **Luka:** niedomknięta kardynalność kontraktu przez brak `OrderSubmittedEvent` w domenie/event-type.

---

## 4) Testy niskiej jakości (wydmuszki) — lista do poprawy

1. `RiskCoordinatorTailerTest.shouldStartReceiveEventsAndClose`  
   - Weryfikuje głównie `size()==2`; brak asercji treści, pochodzenia i kolejności envelope.

2. `RiskCoordinatorEngineTest.shouldEvaluateExposureThresholds`  
   - Sprawdza tylko `desiredState`; brak asercji `totalExposure` i `reason`.

3. `DomainValidationTest` (część przypadków)  
   - Używa `assertDoesNotThrow` bez semantycznych asercji obiektu (np. czy pola faktycznie odpowiadają kontraktowi domenowemu).

4. `EventEnvelopeBinaryContractTest.shouldDeserializeLegacyBinaryWithoutSourceProcessAndCorrelation`  
   - Używa `assertDoesNotThrow` i tylko podstawowe pola; brak kontroli pełnej integralności danych binarnych po deserializacji.

5. Testy liczące wyłącznie ilość eventów (`size`) bez walidacji payload/correlation/source w części modułów common/risk.  
   - Dobre do smoke-check, słabe do wykrywania regresji biznesowych.

---

## 5) Rozjazdy symetrii crypto vs forex

### Ocena: **aktualnie wysoka symetria**
- `ExecutionRuntime`, `ExecutionRuntimeConfig`, `Main`, `ExecutionPropertiesLoader`, testy runtime są prawie 1:1.

### Ryzyka
- Symetria utrzymywana ręcznie (kopiuj-wklej), bez wspólnej abstrakcji.  
- Każda poprawka bezpieczeństwa/stanu wymaga podwójnego wdrożenia; wysoka podatność na przyszły dryf.

### Wniosek
- **Brak aktywnego buga symetrii dziś**, ale **wysokie ryzyko przyszłego rozjazdu**.

---

## 6) Mocne strony kodu

1. Czytelna i spójna implementacja state resolvera (`moreRestrictive`, `canTransitionTo`) i jego użycia w runtime.
2. Dobra separacja odpowiedzialności: emitter, registry, timeout tracker, runtime.
3. Rozszerzony `EventEnvelope` jest wstecznie kompatybilny na poziomie deserializacji (legacy bez `sourceProcessId/correlationId`).
4. Wprowadzony tombstone pattern w `IntentRegistry` poprawia deduplikację przy replay.
5. Symetryczne moduły crypto/forex ułatwiają audyt i porównania zachowań.

---

## 7) Rekomendacje architektoniczne dla Sprint 3.2 i 4

1. **Domknąć risk-coordinator jako komponent decyzyjny:** pełny pipeline od tailingu przez agregację do emisji `RiskDecisionEvent` (z testami integracyjnymi).  
2. **Wprowadzić odporność runtime na wadliwe eventy per-iteracja:** nie dopuścić, by pojedynczy uszkodzony event zatrzymał cały cykl.  
3. **Zdefiniować i wdrożyć pełny kontrakt lifecycle eventów:** dodać brakujące typy i test kardynalności `1 terminal event / correlationId`.  
4. **Ograniczyć memory growth w risk tailerze:** bounded cache/snapshot/streaming agregacji.  
5. **Oddzielić profile uruchomieniowe DEV/TEST od PROD:** `Main` produkcyjny nie powinien startować na in-memory journal + stub broker.  
6. **Zautomatyzować kontrolę ADR compliance:** testy kontraktowe dla `correlationId/sourceProcessId`, checklista CI dla flags/runtime.  
7. **Rozważyć wspólną bazę dla crypto/forex runtime:** zmniejszenie duplikacji i ryzyka dryfu.

---

## Podsumowanie wykonawcze

Kod po Paczkach 1-3 jest wyraźnie stabilniejszy niż pierwotny Sprint 3.1.1, ale nadal zawiera istotne ryzyka architektoniczne poza obszarem naprawionych bugów punktowych. Największy problem systemowy to niedokończony `jwcore-risk-coordinator` (brak emisji decyzji i placeholderowa ekspozycja), co może zablokować realne zarządzanie stanami awaryjnymi w Sprint 3.2/4. Dodatkowo zalecane jest wzmocnienie fail-safe runtime i domknięcie kontraktu event lifecycle względem ADR-015.
