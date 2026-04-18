# JWCore — Sprint 1 Etapu 1

## Zakres
Sprint 1 obejmuje fundament projektu:
- parent POM Maven
- moduły `jwcore-domain`, `jwcore-core`, `jwcore-adapter-jforex`
- definicje domeny i interfejsy graniczne
- implementację `ITimeProvider`
- testy jednostkowe źródeł domeny i core

Zakres świadomie **nie obejmuje**:
- adaptera JForex
- Chronicle Queue runtime/configuracji produkcyjnej
- Graceful Shutdown Hook
- GUI
- Risk Engine
- połączenia z Dukascopy

## Struktura modułów
- `jwcore-domain` — modele domenowe i serializacja binarna `EventEnvelope`
- `jwcore-core` — interfejsy graniczne i dostawcy czasu
- `jwcore-adapter-jforex` — pusty stub modułu pod Sprint 3

## Jak zbudować
W środowisku z Mavenem 3.9+ i Java 21:

```bash
mvn clean test
```

Aby zbudować bez uruchamiania testów:

```bash
mvn clean package -DskipTests
```

## Co zostało zweryfikowane w tej dostawie
- kompilacja wszystkich klas głównych przez `javac --release 21`
- kompletność struktury projektu i modułów
- spójność nazw pakietów i zależności między modułami

## Czego nie udało się zweryfikować w kontenerze
W tym środowisku nie było zainstalowanego polecenia `mvn`, więc nie wykonałem lokalnie:
- `mvn test`
- raportu JaCoCo
- automatycznego pomiaru pokrycia 80%

Testy JUnit 5 zostały **napisane i dołączone do projektu**, ale ich uruchomienie wymaga środowiska z Mavenem.

## Status ukończenia Sprintu 1
Zrobione:
- [x] Parent POM
- [x] Trzy moduły Maven
- [x] `CanonicalId`
- [x] `EventEnvelope`
- [x] `EventType`
- [x] `Tick`
- [x] `Bar`
- [x] `OrderIntent` (stub domenowy)
- [x] `Position` (stub domenowy)
- [x] `ITimeProvider`
- [x] `RealTimeProvider`
- [x] `ControllableTimeProvider`
- [x] Stuby interfejsów granicznych
- [x] Stub modułu `jwcore-adapter-jforex`
- [x] Testy JUnit 5 jako źródła projektu

Do potwierdzenia po uruchomieniu `mvn test`:
- [ ] zielone przejście wszystkich testów
- [ ] raport JaCoCo >= 80% dla `jwcore-domain` i `jwcore-core`

## Decyzje implementacyjne podjęte w Sprincie 1
1. `CanonicalId` jest rekordem z parserem i walidacją zgodności części `Sxx:Iyy` z `VAxx-yy`.
2. `EventEnvelope` ma własną binarną serializację bez frameworków i bez JSON.
3. `payload` w `EventEnvelope` jest defensywnie kopiowany przy zapisie i odczycie.
4. `IdempotencyKeys` używa SHA-256 z wejścia: `broker_order_id + event_type + payload`.
5. `Instrument` jest normalizowany do wielkich liter bez narzucania dziś dodatkowej semantyki brokera.
6. `jwcore-adapter-jforex` pozostaje pustym stubem, żeby nie mieszać Sprintu 1 ze Sprintem 3.

## Pytania i rzeczy do rozstrzygnięcia przed Sprintem 2
1. Czy `event_id` ma zawsze pochodzić z UUID v4, czy w przyszłości przewidujemy własny generator sekwencyjny?
2. Czy `payload` w `EventEnvelope` zostaje surowym binarnym blobem, czy chcemy od razu narzucić wersjonowanie formatu payloadu?
3. Czy `Instrument` ma pozostać prostym stringiem domenowym, czy już w Sprincie 2 wprowadzamy osobny model metadanych instrumentu?
4. W Sprincie 2 trzeba zdecydować, czy Chronicle Queue będzie opakowane własnym interfejsem portu, czy użyte bezpośrednio w warstwie infrastruktury.
