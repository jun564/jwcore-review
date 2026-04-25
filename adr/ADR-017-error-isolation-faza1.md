# ADR-017: Error Isolation: Catch, Emit, Continue (Faza 1)

* **Status:** Proposed
* **Data:** 24.04.2026
* **Autorzy:** Gemini (CTA), GPT (Principal Engineer), Claude (Quality Gate)

---

## 1. Kontekst

W bieżącym stanie repozytorium (HEAD `02b8d09`) występuje krytyczna asymetria w obsłudze błędów procesowania zdarzeń. Moduły `ExecutionRuntime` (Crypto/Forex) stosują wzorzec izolacji, podczas gdy `RiskCoordinatorEngine` w przypadku błędu w metodzie `apply()` jedynie loguje ostrzeżenie i nie emituje zdarzenia porażki.

Dodatkowym ograniczeniem technicznym jest obecny format zdarzenia `EventProcessingFailedEvent`. Jego klucz idempotencji (`IdempotencyKeys.generate(null, EventType.EventProcessingFailedEvent, payload)`) sprawia, że identyczny payload błędu dla tego samego zdarzenia źródłowego zostanie zdeduplikowany przez journal. Uniemożliwia to wiarygodne zliczanie kolejnych prób (retry counter) bez rozszerzenia schematu danych (v3).

Zdecydowano o podziale wdrożenia na dwie fazy. Faza 1 ma na celu natychmiastowe uszczelnienie systemu przy użyciu istniejących struktur danych.

## 2. Decyzja: 6 Inwariantów Izolacji

Wprowadza się następujące zasady obowiązkowe dla wszystkich konsumentów zdarzeń w systemie:

1. **Invariant 1 (Boundary Catch):** Każdy konsument musi posiadać blok `try-catch` na granicy przetwarzania pojedynczego zdarzenia.
2. **Invariant 2 (Fail-Fast on Error):** Zakaz przechwytywania `java.lang.Error` (np. OOM, StackOverflow) – system musi ulec awarii przy błędach krytycznych JVM.
3. **Invariant 3 (Failure Emission):** Po przechwyceniu `java.lang.Exception`, konsument **musi** wyemitować `EventProcessingFailedEvent` (format v2) do journala.
4. **Invariant 4 (Continuity):** Po emisji błędu konsument musi przejść do procesowania kolejnego zdarzenia w journalu.
5. **Invariant 5 (Alerting Loop Protection):** Warstwa dostarczania alertów (`adapter-alerting`) jest zwolniona z obowiązku emisji błędu do journala, aby uniknąć pętli rekurencyjnych (alert o błędzie alertu).
6. **Invariant 6 (Internal Engine Atomicity):** `RiskCoordinatorEngine` musi implementować wzorzec **copy-before-commit** dla swoich wewnętrznych map stanu.

---

## 3. Szczegóły implementacji

### 3.1. Mechanizm copy-before-commit w Risk Engine

`RiskCoordinatorEngine` nie może dokonywać częściowych commitów własnych map (`accountStates`, `canonicalByAccount`, `exposureByAccount`, `unknownTimestamps`) przed zakończeniem walidacji i obliczeń dla danej ścieżki.

* Zmiany w tych mapach mają być wykonywane przez **copy-before-commit**: praca na lokalnej kopii → obliczenia → `AtomicReference.set()` (commit) tylko w przypadku sukcesu.
* **Ważne:** Pełna transakcyjność komponentu `ExposureLedger` pozostaje poza zakresem Fazy 1 i wymaga osobnej pracy nad mechanizmami hardening/recovery.

### 3.2. Produkcja zdarzeń błędu

`RiskCoordinatorEngine` musi posiadać mechanizm budowania i emitowania zdarzeń `EventProcessingFailedEvent`.

* Silnik powinien użyć wspólnej fabryki/komponentu z warstwy `jwcore-core` lub lokalnej minimalnej implementacji.
* **Zakaz:** `risk-coordinator` nie może posiadać zależności do modułów `jwcore-execution-crypto` ani `jwcore-execution-forex` (ryzyko *dependency inversion*).

---

## 4. Konsekwencje

### Pozytywne

* **Resilience:** Silnik ryzyka kontynuuje pracę po wystąpieniu "zatrutych" zdarzeń (*poison pills*).
* **Internal Consistency:** Dla map zarządzanych bezpośrednio przez `RiskCoordinatorEngine` Faza 1 minimalizuje ryzyko częściowego commitu.
* **Visibility:** Każda porażka jest rejestrowana w journalu, umożliwiając audyt techniczny.

### Negatywne

* **Lack of Automation:** W Fazie 1 system nie wykonuje automatycznego retry ani klasyfikacji *permanent failure*. Porażka jest jedynie rejestrowana audytowo; decyzja o ponowieniu lub naprawie pozostaje operacyjna/manualna.
* **Limited Guarantee:** Pełna gwarancja transakcyjności całego stanu ryzyka (w tym `ExposureLedger`) nie jest deklarowana w Fazie 1.

---

## 5. Explicite Out of Scope (odłożone do Fazy 2 / 4C2/C)

* Automatyczny licznik prób (retry counter) i pole `attemptNumber`.
* Flaga `isPermanent` i mechanizm logicznego Dead Letter Queue.
* Nowa wersja binarna (v3) zdarzenia `EventProcessingFailedEvent`.
* Globalne pomijanie zdarzeń przez wszystkie tailery na podstawie statusu błędu.

---

## 6. Alternatywy odrzucone

* **Pełna implementacja (Opcja X):** Odrzucona ze względu na problem z deduplikacją klucza idempotencji w obecnym formacie journala.
* **Disruptor ExceptionHandler:** Odrzucone jako zbyt duża zmiana infrastrukturalna na tym etapie.

## 7. Powiązane ADR

* **ADR-005:** Idempotencja – mechanizm błędu musi chronić spójność stanu.
* **ADR-015:** Correlation ID – błędy powiązane z `failedEventId`.
* **ADR-016:** Risk Decision – zachowanie spójności decyzji po izolacji błędu.

---

## Aktualizacja 25.04.2026 — Faza 2 (Paczka 4C2/C)

Faza 2 implementuje auto-eskalacja zgodna z zasada 37 (System autonomiczny).

### Nowe inwarianty:

**Invariant 7 (Retry Counter):** ProcessingFailureEmitter liczy prob na
podstawie journala (FailureCounter z LRU cache + journal jako source of
truth). Idempotency key = "failed:{failedEventId}:{attemptNumber}".

**Invariant 8 (Permanent Threshold):** attemptNumber >= 3 oznacza
isPermanent=true.

**Invariant 9 (Auto-eskalacja):** RiskCoordinatorEngine po wykryciu
isPermanent=true:
- Znany failedAccountId -> SAFE tego konta
- Nieznany failedAccountId -> SAFE wszystkich kont modulu (zasada 37:
  nieznany kontekst = niepewny stan = SAFE wszystkich)
- Emisja AlertEvent typu PERMANENT_FAILURE z affectedAccounts
- Reset zawsze manualny

### Zmiany w schematach:

- EventProcessingFailedEvent: bump v2 -> v3 (binary, 5 nowych pol)
- AlertEvent: bump v1 -> v2 (binary, pole affectedAccounts)
- Oba ze backward compat dla starych zdarzen w journalu

### Tekst HALT zaktualizowany pod zasade 37:
- Bylo: "Wymaga recznego resetu"
- Po: "Reset wykonywany manualnie po analizie"
