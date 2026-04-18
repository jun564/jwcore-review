# JWCore — Podsumowanie Projektu

**Data:** 18.04.2026 (koniec sesji Claude 12)
**Wersja:** 2.0

---

## Czym jest JWCore

JWCore to własny system tradingowy w Javie 21 budowany od zera przez Architekta + zespół AI (GPT, Gemini, Claude, Claude Code). Zastępuje poprzednią infrastrukturę opartą na MetaTrader 5 (MT5), która została formalnie porzucona 16.04.2026.

**Broker docelowy:** Dukascopy Bank SA, adapter JForex SDK (Etap 1), docelowo FIX Protocol.

**Farma:** `JWCoreFarm` (było `JGGK Farm`). Domena projektu: `jwcorefarm`.

---

## Filozofia architektury

### Pryncypia

1. **Event Sourcing pure** — wszystko idzie przez Chronicle Queue. Zero shared state między procesami.
2. **Hexagonal Architecture** — domena czysta, porty abstrakcyjne, adaptery wymienne.
3. **Izolacja awarii > koszt zasobów** — osobne procesy JVM per rachunek brokerski, per rola.
4. **Ochrona kapitału > cokolwiek innego** — w razie wątpliwości wybieramy konserwatywną ścieżkę.
5. **Człowiek decyduje o kapitale** — automat monitoruje, ostrzega, ogranicza. Nie likwiduje masowo pozycji.
6. **Determinizm > szybkość** — testy reprodukowalne, ControllableTimeProvider, zero System.currentTimeMillis.

### Główne decyzje architektoniczne (skrót)

**Topologia procesów JVM (docelowa):**
- `jwcore-execution-crypto` — execution + lokalne risk dla rachunku krypto
- `jwcore-execution-forex` — execution + lokalne risk dla rachunku forex
- `jwcore-risk-coordinator` — global risk cross-account
- `jwcore-strategy-host` — strategie tradingowe
- `jwcore-control` — procesy kontrolne
- `jwcore-gui` — interfejs użytkownika

**Dwa rachunki Dukascopy** (decyzja 18.04.2026): krypto + forex/surowce, osobne sesje JForex, osobne procesy JVM.

**Tryby awaryjne per rachunek (ADR-011):** RUN / SAFE / HALT / KILL, hierarchia "bardziej restrykcyjny wygrywa".

**Broker likwiduje pozycje przy awarii** — system ich nie zamyka masowo.

**Event Sourcing:** Chronicle Queue 5.25ea16 (pinned), dwie kolejki (market-data + events-business), executor per kolejka, obowiązkowe flagi JVM `--add-opens`.

---

## Zespół AI

### Role i odpowiedzialności

| Członek | Rola | Kompetencje |
|---|---|---|
| **Architekt** | Właściciel projektu, ostateczna decyzja | Trading, MT5 legacy, budżet, wizja |
| **GPT** | Principal Engineer | Kod Java, testy jednostkowe, implementacja |
| **Gemini** | Chief Technical Architect | Architektura, ADR, topologie, low-latency |
| **Claude** | Quality Gate Coordinator | Synteza, krytyka, dokumentacja, prompty |
| **Claude Code** | Implementation Agent | SSH, GitHub, pliki lokalne, build, deploy |

### Zasady komunikacji (obowiązują od 18.04.2026)

1. Każdy mówi za siebie (zakaz "w imieniu zespołu")
2. Każdy w swojej roli
3. Zakaz autoprezentacji
4. Zakaz przekłamań (tylko prawdziwe cytaty)
5. Krytyka wymagana (zakaz przytakiwania)

**Format odpowiedzi:** `[Członek — Rola]:` + meritum od pierwszego zdania.

**Język:** polski wszędzie (korespondencja, GUI, dokumentacja, komentarze). Wyjątki: kod źródłowy (nazwy klas/metod po angielsku), nazwy własne (JWCore, JWCoreFarm).

---

## Stan projektu (18.04.2026)

### Infrastruktura ✅

**Serwer live:** `jwcore-live-01` @ Infomaniak (Satigny/Genewa)
- IP: 83.228.196.250
- Ubuntu 24.04 LTS, 4 vCPU, 12 GB RAM, 250 GB
- OpenJDK 21.0.10, Maven 3.9.15, Git, UFW, Fail2ban
- User `jwcore` (sudo NOPASSWD, klucz SSH), ubuntu zapas, root wyłączony
- €29/mies

**Repo:** https://github.com/jun564/jwcore (prywatne)
**Commit:** 2a701ce (Sprint 1 + Sprint 2 v2)
**Archive MT5:** https://github.com/jun564/jggk-mt5-archive-2026-04-18 (prywatne)

### Sprinty

| Sprint | Status | Zakres |
|---|---|---|
| **Sprint 1** | ✅ Zamknięty | Fundament Maven, domena, core |
| **Sprint 2** | ✅ Zamknięty | Event Sourcing, Chronicle Queue, backpressure szkielety |
| **Sprint 3.1** | ⏳ W toku (jutro nowy czat GPT) | Fundament execution + risk coordinator |
| **Sprint 3.2** | ⛔ Zablokowany przez 3.1 | JForex pełny + reconciliation |
| **Sprint 3.3** | ⛔ Zablokowany przez 3.2 | Risk cross-account + test 72h |
| **Sprint 4** | 🔮 Po Etapie 1 | Strategy Host (blocker: test edge'u BTC) |
| **Sprint 5** | 🔮 Po Sprincie 4 | GUI |

### Zatwierdzone ADR

**Fundamentalne (wcześniejsze sesje):**
- ADR-001 — Time (ITimeProvider, ControllableTimeProvider)
- ADR-003 — Event Sourcing na Chronicle Queue
- ADR-005 — Backpressure trójstopniowy
- ADR-006 — Event Envelope + CanonicalId

**Z sesji Claude 12 (18.04.2026):**
- ADR-006 rozszerzenie — Executor per kolejka CQ + lifecycle
- ADR-008 — Execution jako sole state rebuilder + StateRebuiltEvent
- ADR-009 — OrderTimeoutEvent
- ADR-010 — Margin Monitor w GUI (informacyjny, nie automat)
- ADR-011 — SAFE/HALT/KILL per rachunek + global risk coordinator
- ADR-012 — Pinning Chronicle Queue 5.25ea16
- ADR-013 — Obowiązkowe flagi JVM

---

## Zagrożenia aktualne

### Wysokie

1. **GPT tempo dostarczania** — niezdolność dostarczenia pełnego Sprint 3.1 w jednej turze (18.04). Mitygacja: nowy czat + Wariant 1 (GPT kod → Code build → feedback).
2. **Brak dowodu edge'u strategii BTC strefowej** — otwarty zarzut Claude Quality Gate od Claude 11. Przed Sprintem 4 (Strategy Host) wymagany raport z analizą danych MT5.

### Średnie

3. **Contabo niedostępny** — czekamy na support. Nie blokuje Etapu 1 (Infomaniak wystarczy).
4. **Realna dostępność Architekta** — 5h/dobę, harmonogram Etapu 1 realnie maj-czerwiec 2026.

### Niskie

5. **Dokumenty Dukascopy KYC** — formalność, robione równolegle.
6. **Flagi JVM jako dług** — Project Panama docelowo zastąpi, monitoring CQ releases.

---

## Najważniejsze liczby

- **Sprinty zamknięte:** 2 (Sprint 1, Sprint 2)
- **Testów jednostkowych:** 77 (zielone, 0 failures)
- **Branch coverage:** domain 80.2%, core 83.3%, adapter-cq 80.0%
- **ADR zatwierdzonych:** 13 (z tego 7 w sesji Claude 12)
- **Procesów JVM docelowo:** 6 (3 w Sprincie 3, 2 w Sprincie 4, 1 w Sprincie 5)
- **Kod:** 62 pliki, 2582 linie
- **RAM Infomaniak:** 12 GB (zapas ~5 GB po pełnym deploy)
- **Sesji Claude:** 12 (od pivotu MT5→JWCore: sesje 10, 11, 12)

---

## Plan najbliższy (po sesji 18.04.2026)

### Jutro (19.04.2026)

1. **Nowy czat GPT** — onboarding dokument (`docs/GPT_ONBOARDING.md`)
2. GPT rozpoczyna Sprint 3.1 zgodnie z Wariantem 1 (kod → Code build)
3. Code wykonuje `mvn clean verify` na jwcore-live-01, zwraca wynik
4. Iteracje do zielonego

### Najbliższe dni

- Sprint 3.1 finalizacja
- Code commituje drafty ADR do `adr/` w repo jwcore
- Claude Quality Gate review przed 3.2

### Najbliższe tygodnie (szacunek)

- Sprint 3.2 — koniec kwietnia / początek maja 2026
- Sprint 3.3 + test 72h — maj 2026
- Etap 1 zamknięcie — czerwiec 2026

---

## Kluczowe decyzje do podjęcia

### Wymagające Architekta (pilne)

1. Credentials brokera — clear text .properties vs /etc/jwcore/secrets/ z szyfrowaniem
2. Timeout per rachunek — wartości startowe (propozycja: krypto 15s, forex 30s)
3. Progi risk coordinator cross-account (total exposure, portfolio drawdown) — konkretne liczby

### Wymagające zespołu

4. Test edge'u strategii BTC — metodologia, kryteria akceptacji
5. Quarterly review CQ i JDK releases — kto monitoruje?
6. Strategia migracji na FIX Protocol — kiedy i jak

---

## Dokumenty referencyjne

- **Doc1** — Wizja i cele (nie zmieniony w Claude 12)
- **Doc2 v2.1** — Decyzje projektowe (zaktualizowany 18.04 — dual broker)
- **Doc3A** — ADR-y wcześniejsze (ADR-001, 002, 003, 004, 005)
- **Doc3B** — ADR-y z sesji Claude 12 (ADR-006 ext, 008, 009, 010, 011, 012, 013)
- **Doc4** — Dokument architektoniczny JWCore v1 (w iteracji, po uwagach Claude 11)
- **Doc5 v5.2** — Plan etapów (zaktualizowany 18.04 — Sprint 3 podzielony)
- **Doc6 v6.2** — Backlog (zaktualizowany 18.04 — BL-006 wysoki)

---

## Kontakt operacyjny

**Właściciel:** Architekt (JWCore, JWCoreFarm — nazwy własne, nie podawać imienia)
**Repo GitHub:** jun564/jwcore
**Serwer:** jwcore@83.228.196.250
**Workspace lokalny:** `C:\Users\janus\Desktop\Janusz\SIT Polska\TRADEROWO\schematy AI\Farma JWCore\`
