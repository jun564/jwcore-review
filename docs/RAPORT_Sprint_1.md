# Raport Sprint 1 — JWCore

## Co zostało zrobione
- przygotowana struktura multi-module Maven
- dodane trzy moduły: `jwcore-domain`, `jwcore-core`, `jwcore-adapter-jforex`
- napisane klasy domenowe i core z zakresu Sprintu 1
- dodane testy JUnit 5 jako źródła projektu
- zweryfikowana kompilacja głównych klas przez `javac --release 21`

## Decyzje implementacyjne
- własna serializacja binarna `EventEnvelope`, bez zależności frameworkowych
- `CanonicalId` jako rekord z twardą walidacją formatu i zakresów
- `ControllableTimeProvider` sterowany bez rzeczywistego czekania
- `OrderIntent` i `Position` jako stubs domenowe gotowe na dalsze etapy

## Niewykonane w kontenerze
- uruchomienie `mvn test`
- raport pokrycia JaCoCo

Powód: w kontenerze brak zainstalowanego polecenia `mvn`.

## Co wymaga rozstrzygnięcia przed Sprintem 2
- wersjonowanie payloadu w `EventEnvelope`
- dalszy model `Instrument` i metadanych instrumentów
- sposób opakowania Chronicle Queue w warstwie infrastruktury
