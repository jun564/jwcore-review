# JWCore — Fundament systemu transakcyjnego

Wielomodułowy projekt Java 21 / Maven implementujący rdzeń silnika transakcyjnego JWCore.

## Status Sprintów

| Sprint | Zakres | Status | Branch coverage |
|--------|--------|--------|-----------------|
| Sprint 1 | Domain model, EventEnvelope, CanonicalId, BinaryCodec | ukończony | 81.7% |
| Sprint 2 v2 | IEventJournal, OrderTimeoutMonitor, BackpressureController, Chronicle Queue adapter | ukończony | domain 80.2% / core 83.3% / cq 80.0% |

## Struktura repozytorium

```
/src        -- kod źródłowy (Maven multi-module, Java 21)
/docs       -- raporty sprintów i dokumentacja
/adr        -- Architecture Decision Records
/prompts    -- prompty sesji projektowych
README.md   -- ten plik
```

## Moduły (src/)

| Moduł | Opis |
|-------|------|
| jwcore-domain | Model domenowy: EventEnvelope, CanonicalId, Bar, Tick, OrderIntent, IdempotencyKeys |
| jwcore-core | Porty (IEventJournal, TailSubscription), OrderTimeoutMonitor, BackpressureController, TimeProvider |
| jwcore-adapter-jforex | Adapter JForex (szkielet) |
| jwcore-adapter-cq | Adapter Chronicle Queue 5.25ea16 |

## Wymagania

- Java 21+
- Maven 3.9+

## Budowanie

```bash
cd src
mvn clean verify
```
