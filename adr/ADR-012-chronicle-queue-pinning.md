# ADR-012 — Pinning Chronicle Queue 5.25ea16

**Data:** 18.04.2026
**Status:** Zatwierdzony (Gemini + GPT + Claude + Architekt)
**Kontekst:** Sprint 2 ujawnił niekompatybilność Chronicle Queue 2026.2 z Java 21 Temurin LTS. Po downgrade do 5.25ea16 wszystkie testy przeszły zielone, BUILD SUCCESS na Ubuntu 24.04.

## Decyzja

**Wersja Chronicle Queue w JWCore: 5.25ea16 (pinned).**

Nie aktualizujemy wersji bez spełnienia wszystkich warunków:

1. Walidacja na środowisku testowym (`mvn clean verify` zielony)
2. Test 72h stabilności na staging Infomaniak
3. Akceptacja zespołu (GPT + Gemini + Claude)
4. Aktualizacja tego ADR z datą i uzasadnieniem

## Uzasadnienie empiryczne (dopisek GPT #5)

Wersja 5.25ea16 **potwierdzona buildem i testami** na:
- Ubuntu 24.04 LTS
- Java 21 Temurin LTS (21.0.10)
- Maven 3.9.15
- Infomaniak VPS 4 vCPU / 12 GB RAM / 250 GB
- 77 testów jednostkowych, 0 failures
- `BUILD SUCCESS` we wszystkich modułach
- Branch coverage ≥80%

**To nie jest decyzja teoretyczna** — wersja działa w naszym docelowym środowisku runtime.

## Uzasadnienie strategiczne

- **2026.2 to bleeding-edge** — nowa ale niestabilna z Java 21
- **5.25ea16 to stabilna gałąź LTS** — testy pełne zielone
- **Zasada: stabilność > nowość** dla komponentu krytycznego (kręgosłup Event Sourcing)
- **Precedens z innych projektów HFT** — Chronicle wersje 5.x są sprawdzone w produkcji

## Monitorowanie

Zespół obserwuje release notes Chronicle Queue:
- Nowe wersje 5.26.x (jeśli pojawią się stabilne releases)
- Wersje z pełnym wsparciem Java 21+ (bez konieczności `--add-opens`)
- Project Panama (FFM API) — docelowo zastąpi `sun.nio.ch` hacks, wtedy warto rozważyć update

Aktualizacja do nowszej stabilnej wersji **rozważana** po spełnieniu 4 warunków powyżej.

## Konsekwencje

**Pozytywne:**
- Przewidywalne zachowanie w produkcji
- Zespół zna zachowanie tej wersji
- Brak niespodzianek przy deploy

**Negatywne:**
- Brak najnowszych features Chronicle Queue (jeśli jakieś są krytyczne)
- Dług techniczny — kiedyś trzeba będzie migrować

**Mitygacja:**
- Monitorowanie release notes (quarterly review)
- Rozpoznawanie ryzyk przed każdą decyzją o migracji

## Zależności

- ADR-003 (Event Sourcing na Chronicle Queue)
- ADR-013 (Flagi JVM — specyficzne dla CQ 5.25ea16)
- Java 21 Temurin LTS
