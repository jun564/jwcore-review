# Raport Sprint 2 — JWCore

## Co zostało zrobione
- dodany port `IEventJournal` i `TailSubscription`
- dodany moduł `jwcore-adapter-cq`
- przygotowana implementacja `ChronicleQueueEventJournal` z dwiema kolejkami (`market-data`, `events-business`)
- dodane struktury backpressure i testy przejść stanów
- dodany `GracefulShutdownCoordinator`
- dodany `OrderTimeoutMonitor` z emisją `OrderTimeoutEvent`
- `EventEnvelope` rozszerzony o `payloadVersion`
- `EventType` rozszerzony o `OrderTimeoutEvent`

## Decyzje implementacyjne
- `IEventJournal.read(from, to)` przyjmuje zakres czasu `Instant`
- cross-queue ordering przy odczycie: `timestampEvent` + `eventId`
- `ChronicleQueueEventJournal.flush()` istnieje jako metoda adaptera, ale nie przecieka do portu `IEventJournal`
- backpressure optional tailers mogą wywołać alert, ale nie wymuszają HALT

## Czego nie zweryfikowano w tym środowisku
- pełnego `mvn clean verify` (brak Maven w kontenerze)
- integracji runtime Chronicle Queue z realnym systemem Linux
- testu 72h stabilności

## Pytania przed Sprintem 3
1. Czy `read(from, to)` ma pozostać czasowe, czy przejść na zakres indeksów journala?
2. Czy `tail()` ma startować od końca czy wspierać tryb od początku dla wybranych konsumentów?
3. Jakie dokładnie placeholdery liczbowych progów backpressure przyjąć po wyborze serwera?
4. Czy `OrderTimeoutEvent` ma mieć osobny payload model binarny, czy wystarczy prosty payload tekstowy?
