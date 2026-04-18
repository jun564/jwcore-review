# JWCore — Sprint 2

## Co zawiera paczka
- port `IEventJournal` w `jwcore-core`
- adapter Chronicle Queue w `jwcore-adapter-cq`
- szkielety backpressure, graceful shutdown i order timeout
- poprawki testów gałęziowych
- konfigurację Surefire/Failsafe z `--add-opens`

## Jak uruchomić
```bash
mvn clean verify
```

## Uwaga
Do uruchomienia testów i runtime Chronicle Queue wymagane są flagi JVM zapisane w `pom.xml`. Lista tych flag jest także w raporcie `RAPORT_Sprint_2_v2.md`.
