# ADR-015 — Event Correlation and Provenance Tracking

**Status:** ACCEPTED
**Data:** 2026-04-19
**Autor:** Gemini (architektura), GPT (implementacja)
**Dotyczy:** jwcore-domain, jwcore-execution-*, jwcore-risk-coordinator

---

## Kontekst

System JWCore wchodzi w etap wieloprocesowy (execution nodes, risk-coordinator).
Dotychczasowy model zdarzeń nie umożliwia:

- śledzenia pochodzenia zdarzenia (który proces je wygenerował),
- korelacji zdarzeń należących do jednego cyklu życia zlecenia,
- deterministycznego replay i audytu end-to-end.

Sprint 3.1.1 wprowadził pola `sourceProcessId` i `correlationId` w `EventEnvelope`, ale bez formalnej specyfikacji.

---

## Decyzja

Wprowadzamy formalny model:

### 1. sourceProcessId

- Typ: `String`
- Wymagany dla każdego nowego zdarzenia
- Deterministyczny identyfikator węzła (np. `crypto-execution-node-1`)
- Wstrzykiwany z konfiguracji (`ExecutionRuntimeConfig.nodeId`)
- Nie może być losowy (zakaz UUID runtime)

---

### 2. correlationId

- Typ: `UUID`
- Tworzony w momencie powstania `OrderIntent`
- Dziedziczony przez wszystkie zdarzenia pochodne

---

### 3. Zasady dziedziczenia

| Typ zdarzenia | correlationId |
|---|---|
| OrderIntentEvent | generowany |
| Event pochodny (submitted/rejected/timeout) | dziedziczony |
| Event reaktywny (na inny event) | dziedziczony |
| Event systemowy (np. StateRebuiltEvent) | null dopuszczalny |

---

### 4. Kardynalność korelacji

Dla jednego `correlationId`:

- maksymalnie jedno zdarzenie kończące:
  - `OrderSubmittedEvent`
  - `OrderRejectedEvent`
  - `OrderTimeoutEvent`

Zdarzenia te są **wzajemnie wykluczające się**.

Cel:
- jednoznaczny stan zlecenia
- deduplikacja
- prosty tracking

---

### 5. Wpływ na replay i deserializację

- `EventEnvelope` z `sourceProcessId` i `correlationId` stanowi nowy kontrakt binarny
- stare zdarzenia mogą nie zawierać tych pól

Wymagania:

- `sourceProcessId` → może być `null` przy replay starych danych
- `correlationId` → może być `null` przy replay starych danych
- deserializer musi być wstecznie kompatybilny

---

## Konsekwencje

### Pozytywne

- pełna śledzalność zdarzeń (audit trail)
- możliwość rekonstrukcji flow zlecenia
- przygotowanie pod system rozproszony (multi-node)
- uproszczona diagnostyka i debugging

### Negatywne / ryzyka

- konieczność utrzymania kompatybilności wstecznej
- zwiększenie rozmiaru zdarzeń
- wymóg aktualizacji testów i emitterów

---

## Wymagania implementacyjne

1. Dodanie `nodeId` do `ExecutionRuntimeConfig`
2. Wymuszenie `sourceProcessId` przy tworzeniu każdego `EventEnvelope`
3. Generowanie `correlationId` dla `OrderIntent`
4. Propagacja `correlationId` w runtime
5. Test binarny kontraktu (`serialize/deserialize`)
6. Test dziedziczenia korelacji

---

## Powiązania

- ADR-014 — Flat Module Layout
- Sprint 3.1.1 — rozszerzenie EventEnvelope
- Sprint 3.1.2 — implementacja korelacji i zdarzeń odrzucenia
