# ADR-014: Standaryzacja layoutu repozytorium (flat Maven multi-module)

**Status:** Zaakceptowany
**Data:** 19.04.2026
**Autor decyzji:** Gemini (Chief Technical Architect)
**Akceptacja:** Architekt
**Kontekst iteracji:** Sprint 3.1.1 merge do repo jwcore

## Kontekst

Repozytorium jun564/jwcore znajduje sie w stanie rozbieznosci strukturalnej:

Sprint 1 i 2 (obecny main): layout nested z folderem nadrzednym src/ w korzeniu
- src/jwcore-domain/
- src/jwcore-core/
- src/jwcore-adapter-cq/
- src/jwcore-adapter-jforex/

Paczka Sprint 3.1.1 (od GPT): layout flat - moduly bezposrednio w korzeniu
- jwcore-domain/
- jwcore-core/
- jwcore-execution-common/
- jwcore-execution-crypto/
- itd.

Przed mergem paczki 3.1.1 do main konieczna jest decyzja architektoniczna ustanawiajaca standard.

## Decyzja

Przyjmujemy layout flat jako obowiazujacy standard projektu JWCore.

Wszystkie moduly Maven umieszczane sa bezposrednio w katalogu glownym repozytorium, obok glownego pom.xml rodzica. Nie stosujemy folderu nadrzednego src/.

Struktura docelowa:
- jwcore/
- pom.xml (parent)
- jwcore-domain/
- jwcore-core/
- jwcore-adapter-cq/
- jwcore-adapter-jforex/
- jwcore-execution-common/
- jwcore-execution-crypto/
- jwcore-execution-forex/
- jwcore-risk-coordinator/
- docs/
- adr/
- reports/
- .github/workflows/

## Uzasadnienie

1. Zgodnosc ze standardami Maven multi-module: w Spring Boot, Apache Kafka, Apache Cassandra moduly sa bezposrednio obok parent pom.xml. Folder src/ w korzeniu jest redundantny.

2. Ergonomia pracy AI i IDE: agenci AI (Code, Claude, GPT, Gemini) operuja na sciezkach relatywnych. Skrocenie sciezki o segment src/ zmniejsza ryzyko bledow parsowania. IDE (IntelliJ, Eclipse) automatycznie rozpoznaja moduly jako projekty zrodlowe.

3. Uproszczenie CI/CD: workflow GitHub Actions (w tym istniejacy sync-to-review.yml) oraz potencjalna konteneryzacja Docker maja prostsza konfiguracje kontekstu budowania.

4. Spojnosc z forma dostarczania iteracji przez GPT: paczka Sprint 3.1.1 przyszla od GPT w formie flat. Przyjecie tego standardu eliminuje koniecznosc restrukturyzacji przy kazdej iteracji.

## Konsekwencje

Pozytywne:
- Ujednolicony standard na wszystkie przyszle sprinty
- Kompatybilnosc ze standardami Maven
- Skrocenie sciezek w promptach dla zespolu AI
- Prostsza konfiguracja CI/CD i narzedzi

Negatywne:
- Jednorazowy koszt migracji Sprint 1 i 2 (operacja mechaniczna)
- Koniecznosc weryfikacji wszystkich skryptow pomocniczych pod katem sciezek

## Plan implementacji

Migracja odbywa sie w ramach galezi sprint-3-1-1-merge przez Code, nie w Sprincie 3.1.2.

Kolejnosc commitow w galezi sprint-3-1-1-merge:
1. docs: zachowanie historycznych dokumentow Sprint 1 i 2
2. refactor: migracja layoutu src/modul -> flat [ADR-014]
3. feat(sprint-3.1.1): wprowadzenie 4 nowych modulow execution + lokalne fixy
4. docs: dodanie ADR-014 standaryzacja layoutu repozytorium

Po weryfikacji Quality Gate i akceptacji Architekta - merge do main przez GPT.

## Wymogi dla zespolu po akceptacji ADR-014

GPT (Principal Engineer):
- Wszystkie przyszle iteracje dostarczane w layoucie flat
- Parent pom.xml utrzymuje sekcje modules zgodna z layoutem flat

Code (Implementation Agent):
- Wszystkie skrypty pomocnicze, CI/CD, konfiguracje buildu uzywaja layoutu flat
- Dokumentacja techniczna (docs/architektura.md, README.md) aktualizowana pod katem nowych sciezek

Gemini (Chief Technical Architect):
- Kolejne ADR uzywaja sciezek flat gdy odnosza sie do plikow kodu

Claude (Quality Gate):
- Weryfikuje w Fazie 4 iteracji czy layout pozostaje zgodny z ADR-014
- Zatrzymuje iteracje gdy struktura repo odbiega od flat

## Powiazane ADR

- ADR-012 - Chronicle Queue pinning (sciezki do katalogow danych)
- ADR-013 - Obowiazkowe flagi JVM (sciezki do modulow testowych)

Oba ADR nie wymagaja zmian - layout flat jest kompatybilny z ich wymogami.
