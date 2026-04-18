# Aneks Sprint 2 v2 — Walidacja produkcyjna

**Data:** 18.04.2026
**Status:** Zamknięty formalnie

## Przebieg iteracji naprawczej

Sprint 2 v1 (iteracja wstępna) wykazał BUILD FAILURE w module `jwcore-adapter-cq` przy testach Chronicle Queue. Przyczyną był `IllegalAccessException` na `sun.nio.ch.FileDispatcherImpl.map0` — hermetyzacja Java 21 blokowała dostęp.

Po decyzji zespołu (Claude, Gemini, GPT + Architekt) wdrożono **Opcję A — naprawa w miejscu**:

1. Konfiguracja `maven-surefire-plugin` i `maven-failsafe-plugin` z `--add-opens`
2. Downgrade Chronicle Queue 2026.2 → **5.25ea16** (stabilna gałąź LTS)
3. Rozszerzenie testów branch coverage w `jwcore-domain` i `jwcore-core`

## Walidacja na serwerze live

Build uruchomiony na `jwcore@83.228.196.250` (Infomaniak VPS, Ubuntu 24.04 LTS, OpenJDK 21.0.10, Maven 3.9.15):

```
mvn clean verify
```

**Wynik: BUILD SUCCESS** we wszystkich modułach.

### Liczby testów

- `jwcore-domain`: 46 testów, 0 failures
- `jwcore-core`: 27 testów, 0 failures
- `jwcore-adapter-cq`: 4 testy, 0 failures
- **Razem: 77 testów, 0 failures**

### Branch coverage JaCoCo

| Moduł | Branch | Instruction | Line | Method |
|---|---|---|---|---|
| jwcore-domain | **80.2%** (101/126) ✅ | 96.3% | 95.7% | 97.4% |
| jwcore-core | **83.3%** (60/72) ✅ | 95.9% | 94.9% | 97.1% |
| jwcore-adapter-cq | **80.0%** (16/20) ✅ | 96.9% | 95.2% | 100.0% |

**Wszystkie 3 moduły przekraczają próg 80% branch coverage.**

## Wartość dodana Code podczas walidacji

Code (Implementation Agent) podczas uruchamiania `mvn clean verify` wykrył i naprawił **race condition** w metodzie `ChronicleQueueEventJournal.tail()`:

**Problem:** Tailer nie był pozycjonowany na końcu kolejki przed powrotem z metody `tail()`. Event mógł trafić do kolejki zanim `toEnd()` zostanie wykonane → gubiliśmy eventy.

**Naprawa:** Dodano `CountDownLatch ready` — metoda `tail()` nie wraca do caller'a zanim wątek tailera nie potwierdzi gotowości (pozycja `toEnd()`).

**To nie była pomyłka GPT** — to była poprawka jakościowa którą zespół przeoczył. Zapisano do dokumentacji jako dobry precedens Quality Gate przez Code.

## Poprawki zespołu podczas iteracji

### Architekt → zespół
- Zatwierdzenie Opcji A (naprawa w miejscu, nie odłożenie CQ)

### Gemini → GPT
- Wskazanie przyczyny root: `maven-surefire-plugin` forkuje JVM bez dziedziczenia flag
- Zarządzenie: konfiguracja `<argLine>` z `--add-opens`

### GPT → Gemini
- Korekta: `--add-opens` rozwiązuje TESTY, ale musi być też w RUNTIME (systemd)
- Dopisek długu operacyjnego

### Claude Quality Gate
- Synteza decyzji, prompt końcowy dla GPT
- Propozycja ADR-012 (pinning CQ) i ADR-013 (flagi JVM) po iteracji

## Wymagane flagi JVM (produkcyjne)

Ta lista jest **minimalnym zestawem potwierdzonym empirycznie** dla Java 21 Temurin + CQ 5.25ea16 (szczegóły → ADR-013):

```
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED
--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED
```

**Obowiązują:** Surefire, Failsafe, systemd unit files produkcyjne (Sprint 3+), IDE, Docker.

## Zatwierdzone ADR powstałe w wyniku Sprint 2 v2

- **ADR-012** — Pinning Chronicle Queue 5.25ea16 (zob. osobny dokument)
- **ADR-013** — Obowiązkowe flagi JVM (zob. osobny dokument)

## Status Sprintu 2

**ZAMKNIĘTY FORMALNIE** 18.04.2026.

Sprint 2 dostarczył pełne fundamenty Event Sourcing:
- ✅ IEventJournal (port w jwcore-core)
- ✅ ChronicleQueueEventJournal (implementacja w jwcore-adapter-cq)
- ✅ Dwie kolejki: `market-data` + `events-business`
- ✅ EventEnvelope z payloadVersion
- ✅ GracefulShutdownCoordinator
- ✅ Szkielety backpressure (trójstopniowy)
- ✅ OrderTimeoutEvent + timeout tracking
- ✅ Race condition w tail() naprawiony

Gotowy grunt pod Sprint 3 (Execution Adapter, JForex, Risk Coordinator).

## Pytania otwarte przeniesione do Sprintu 3

(Z `RAPORT_Sprint_2_v2.md` — 3 pytania GPT)

1. **Czy runtime Linux/systemd ma przyjąć te same `--add-opens`?**
   **Odpowiedź zespołu (18.04):** TAK, te same flagi w systemd. Zamknięte przez ADR-013.

2. **Czy `5.25ea16` zostaje czy wracamy do nowszej gałęzi?**
   **Odpowiedź zespołu (18.04):** ZOSTAJE. Zamknięte przez ADR-012.

3. **Czy tailing CQ ma mieć osobny executor per kolejka już w Sprincie 3?**
   **Odpowiedź zespołu (18.04):** TAK, osobny executor per kolejka. Zamknięte przez rozszerzenie ADR-006.

## Commit repozytorium

**Hash:** `2a701ce`
**Wiadomość:** "Sprint 1 + Sprint 2 v2 — fundament JWCore"
**URL:** https://github.com/jun564/jwcore

**Zawartość:**
- `src/` — Maven multi-module (4 moduły)
- `docs/RAPORT_Sprint_1.md`, `RAPORT_Sprint_2.md`, `RAPORT_Sprint_2_v2.md`, `README_Sprint_1.md`, `README_Sprint_2.md`
- `adr/.gitkeep` (ADR wieczorne commits)
- `prompts/.gitkeep` (prompty wieczorne commits)
- 62 pliki, 2582 linie kodu
