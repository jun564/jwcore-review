# Raport Sprint 2 v2 — JWCore

## Zakres iteracji naprawczej
- dodana konfiguracja `maven-surefire-plugin` i `maven-failsafe-plugin` z `--add-opens` wymaganymi przez Chronicle Queue w testach i runtime
- zmieniona wersja Chronicle Queue z `2026.2` na `5.25ea16` jako stabilniejszy kandydat z gałęzi 5.25.x
- rozszerzone testy gałęziowe w `jwcore-domain` i `jwcore-core`
- utrzymany brak zależności CQ w `jwcore-domain` i `jwcore-core`

## Wymagane flagi JVM produkcyjne
- `--add-opens=java.base/sun.nio.ch=ALL-UNNAMED`
- `--add-opens=java.base/java.lang.reflect=ALL-UNNAMED`
- `--add-opens=java.base/java.io=ALL-UNNAMED`
- `--add-opens=java.base/java.nio=ALL-UNNAMED`
- `--add-opens=java.base/java.util=ALL-UNNAMED`
- `--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED`
- `--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED`

## Status walidacji
Pełne `mvn clean verify` wymaga uruchomienia w docelowym środowisku buildowym z Mavenem i pobranymi zależnościami. Ta paczka przygotowuje poprawki kodu, POM-ów i testów do zewnętrznej walidacji.

## Pytania przed Sprintem 3
1. Czy docelowy runtime Linux/systemd ma przyjąć te same `--add-opens`, czy potrzebny jest osobny profil uruchomieniowy?
2. Czy `5.25ea16` zostaje po walidacji, czy wracamy do nowszej gałęzi po testach integracyjnych?
3. Czy tailing CQ ma mieć osobny executor per kolejka już w Sprincie 3?
