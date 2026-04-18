# JWCore

Własny system tradingowy w Java 21, podłączony do Dukascopy Bank SA via JForex SDK (Etap 1), docelowo FIX Protocol.

## Stan projektu (18.04.2026)

| Sprint | Status | Zakres |
|---|---|---|
| Sprint 1 | ✅ Zamknięty | Fundament Maven, domena, core |
| Sprint 2 | ✅ Zamknięty | Event Sourcing, Chronicle Queue 5.25ea16 |
| Sprint 3.1 | ⏳ W toku | Execution runtime + risk coordinator szkielet |
| Sprint 3.2 | 🔒 Zablokowany | JForex pełny + Reconciliation |
| Sprint 3.3 | 🔒 Zablokowany | Risk cross-account + test 72h |

## Architektura

**Hexagonal Architecture + Event Sourcing**

Moduły:
- `src/jwcore-domain` — domena (EventType, Timeframe, CanonicalId, EventEnvelope)
- `src/jwcore-core` — porty (IEventJournal, interfejsy)
- `src/jwcore-adapter-jforex` — adapter Dukascopy (stub w Sprint 2, pełny w Sprint 3.2)
- `src/jwcore-adapter-cq` — adapter Chronicle Queue

Docelowe procesy JVM (Sprint 3+):
- `jwcore-execution-crypto` — execution dla rachunku krypto
- `jwcore-execution-forex` — execution dla rachunku forex/surowce
- `jwcore-risk-coordinator` — global risk cross-account
- `jwcore-strategy-host` — strategie tradingowe (Sprint 4)
- `jwcore-control` — procesy kontrolne (Sprint 4)
- `jwcore-gui` — interfejs (Sprint 5)

## Wymagania środowiska

- **Java:** 21 Temurin LTS
- **Maven:** 3.9+
- **OS:** Ubuntu 24.04 LTS (produkcja) lub Windows (development)

**Obowiązkowe flagi JVM (patrz `adr/ADR-013`):**
```
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED
--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED
```

## Build i test

```bash
cd src/
mvn clean verify
```

Wynik Sprint 2 (walidacja na Infomaniak 18.04.2026):
- 77 testów, 0 failures
- Branch coverage: domain 80.2%, core 83.3%, adapter-cq 80.0%
- BUILD SUCCESS

## Struktura repozytorium

```
jwcore/
├── README.md                         # ten plik
├── .gitignore
├── src/                              # kod Maven multi-module
│   ├── pom.xml
│   ├── jwcore-domain/
│   ├── jwcore-core/
│   ├── jwcore-adapter-jforex/
│   └── jwcore-adapter-cq/
├── adr/                              # Architecture Decision Records
│   ├── ADR-006-extension-executor-per-queue.md
│   ├── ADR-008-sole-state-rebuilder.md
│   ├── ADR-009-order-timeout-event.md
│   ├── ADR-010-margin-monitor-gui.md
│   ├── ADR-011-execution-states-per-account.md
│   ├── ADR-012-chronicle-queue-pinning.md
│   └── ADR-013-mandatory-jvm-flags.md
├── docs/                             # dokumentacja projektu
│   ├── RAPORT_Sprint_1.md
│   ├── README_Sprint_1.md
│   ├── RAPORT_Sprint_2.md
│   ├── RAPORT_Sprint_2_v2.md
│   ├── README_Sprint_2.md
│   ├── ANEKS_Sprint_2_v2.md
│   ├── GPT_ONBOARDING.md             # do użycia z nowym czatem GPT
│   └── sprint3-backlog.md            # plan iteracji 3.1/3.2/3.3
└── prompts/                          # prompty sesji zespołu
    └── prompty_sesja_claude_12.md
```

## Zespół AI

| Członek | Rola |
|---|---|
| Architekt | Właściciel projektu |
| GPT | Principal Engineer (kod, testy) |
| Gemini | Chief Technical Architect |
| Claude | Quality Gate Coordinator |
| Claude Code | Implementation Agent (SSH, build, deploy) |

## Język projektu

Polski wszędzie: korespondencja, GUI, dokumentacja, komentarze.
Wyjątki: kod źródłowy (nazwy klas/metod po angielsku wg konwencji Java), nazwy własne (JWCore).

## Poprzednia infrastruktura

System poprzedni (MT5) został archiwizowany 18.04.2026:
- Repo archive: https://github.com/jun564/jggk-mt5-archive-2026-04-18 (prywatne)
- Pivot decyzja: 16.04.2026 (sesja Claude 10)
