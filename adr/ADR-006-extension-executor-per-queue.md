# ADR-006 Rozszerzenie — Executor per kolejka Chronicle Queue

**Data:** 18.04.2026
**Status:** Zatwierdzony (Gemini + GPT + Claude + Architekt)
**Kontekst rozszerzenia:** Decyzja Gemini z 18.04.2026 w odpowiedzi na pytanie GPT z RAPORT_Sprint_2_v2.md ("Czy tailing CQ ma mieć osobny executor per kolejka już w Sprincie 3?")

## Decyzja

**Każda kolejka Chronicle Queue ma osobny executor (dedykowany wątek) do tailowania.**

- `market-data` → `ExecutorService marketDataExecutor` (1 wątek)
- `events-business` → `ExecutorService businessEventsExecutor` (1 wątek)

## Uzasadnienie

- Market-data jest strumieniem tick-by-tick o wysokiej częstotliwości
- Events-business zawiera krytyczne eventy (OrderIntent, OrderTimeoutEvent, StateRebuiltEvent, RiskDecisionEvent)
- Współdzielony executor → market-data może opóźnić reakcję na OrderTimeoutEvent
- Low-latency wymaga fizycznej separacji wątków konsumujących
- Żaden "tłok" w danych rynkowych nie może opóźnić przetwarzania intencji transakcyjnych

## Implementacja

- Executor tworzony **per komponent tailujący** (Strategy Host, Risk Engine, GUI, Execution)
- **Nie współdzielony** między komponentami — każdy tworzy własne executory
- `ThreadFactory` nadaje nazwy typu:
  - `jwcore-md-tailer-strategy`
  - `jwcore-md-tailer-risk`
  - `jwcore-be-tailer-strategy`
  - `jwcore-be-tailer-execution-crypto`

## Lifecycle executorów

- Każdy executor ma **bounded lifecycle** związany z właścicielem komponentu
- Shutdown executora jest **częścią GracefulShutdownCoordinator** — wywoływany w sekwencji shutdown
- Sekwencja shutdown per executor:
  1. `executor.shutdown()` — nie przyjmuje nowych zadań
  2. `executor.awaitTermination(10, SECONDS)` — czeka max 10s na zakończenie bieżących zadań
  3. Jeśli timeout: `executor.shutdownNow()` — interrupt wszystkich zadań + log WARN
  4. Kolejne `awaitTermination(2, SECONDS)` — fail-safe
  5. Jeśli dalej wiszą wątki — log ERROR z listą nazw wątków
- **Brak wiszących tailerów po zamknięciu procesu** — wymóg operacyjny (uzupełnienie od GPT)

## Konsekwencje

**Pozytywne:**
- Izolacja wpływu jednej kolejki na drugą
- Przewidywalne czasy przetwarzania eventów krytycznych
- Zgodność ze standardami systemów low-latency

**Negatywne:**
- Więcej wątków JVM (2× więcej niż w modelu współdzielonym)
- Większa złożoność shutdown

**Mitygacja:**
- 2 dodatkowe wątki to pomijalny koszt pamięci (~1-2 MB stack size każdy)
- Shutdown sequencing już wymagany przez ADR-006 główny

## Zależności

- Wymaga współpracy z GracefulShutdownCoordinator (Sprint 2, rozszerzenie w Sprint 3)
- Obowiązuje wszystkie procesy JVM tailujące CQ (execution, risk-coordinator, strategy-host, control, gui)
