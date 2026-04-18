# ADR-013 — Obowiązkowe flagi JVM

**Data:** 18.04.2026
**Status:** Zatwierdzony (Gemini + GPT + Claude + Architekt)
**Kontekst:** Chronicle Queue 5.25ea16 wymaga dostępu do klas `sun.nio.ch.*` i innych zamkniętych pakietów Javy 21. Hermetyzacja JDK 17+ (JEP 396/403) blokuje ten dostęp domyślnie. Problem ujawniony w Sprincie 2: `maven-surefire-plugin` forkuje JVM testowy bez dziedziczenia flag, co spowodowało `IllegalAccessException` przy testach CQ.

## Decyzja

**Wszystkie procesy JVM JWCore uruchamiane z poniższym zestawem flag.**

## Status listy (dopisek GPT #4)

**To jest minimalny zestaw potwierdzony empirycznie dla:**
- Java 21 Temurin LTS
- Chronicle Queue 5.25ea16

**Każda zmiana JDK** (upgrade, inny vendor — OpenJDK, Zulu, Corretto) **lub CQ** (inna wersja) **wymaga ponownej walidacji** przez pełny `mvn clean verify` + test 72h stabilności.

**Brak którejkolwiek flagi = unsupported runtime.**

Lista **nie jest gwarancją kompletności** dla przyszłych wersji dependencies.

## Minimalny zestaw flag

```
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED
--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED
```

## Obszary zastosowania (obowiązkowo wszędzie)

### Build & Test
- `maven-surefire-plugin` `<argLine>` — testy jednostkowe
- `maven-failsafe-plugin` `<argLine>` — testy integracyjne
- CI/CD pipeline (gdy powstanie)

### Produkcja
- **systemd unit files** dla wszystkich procesów:
  - `jwcore-execution-crypto.service`
  - `jwcore-execution-forex.service`
  - `jwcore-risk-coordinator.service`
  - `jwcore-strategy-host.service` (Sprint 4)
  - `jwcore-control.service` (Sprint 4)
  - `jwcore-gui.service` (Sprint 5)

### Development
- IntelliJ IDEA — VM options w Run Configurations
- Eclipse — JVM arguments w Run Configuration
- Skrypty startowe lokalne (`start-dev.sh`, `start-dev.bat`)

### Opcjonalnie w przyszłości
- Docker image entrypoint (jeśli projekt przejdzie na konteneryzację)
- Kubernetes deployment spec (jeśli będzie cluster)

## Uzasadnienie

### Techniczne

**Chronicle Queue wymaga bezpośredniego dostępu do low-level primitives:**
- `sun.nio.ch.FileDispatcherImpl.map0()` — memory-mapped files
- `sun.misc.Unsafe` — direct memory manipulation, atomic operations
- `java.nio.DirectByteBuffer` — off-heap buffers
- `jdk.internal.ref.Cleaner` — cleanup off-heap memory

Te API są częścią implementacji JVM, nie publicznym API. Hermetyzacja (JPMS, JEP 396) blokuje ich użycie domyślnie w Javie 17+.

**Project Panama (Foreign Function & Memory API)** docelowo zastąpi te hacki. Do tego czasu `--add-opens` to **oficjalny i udokumentowany** sposób obsługi Chronicle Queue (potwierdzone w release notes CQ 5.x).

### Strategiczne

- Systemy low-latency/HFT używają Unsafe i mmap rutynowo
- Zdefiniowana zależność runtime, nie ukryta
- Jednolita konfiguracja dev/test/prod — brak "działa u mnie, nie działa w produkcji"

## Charakter flag

**To NIE jest:**
- "brudny hack"
- "tymczasowe obejście"
- "dług technologiczny do usunięcia w następnej iteracji"

**To JEST:**
- Oficjalny, udokumentowany wymóg dostawcy biblioteki (Chronicle Software)
- Standard branżowy dla systemów low-latency na Java 17+
- Część kontraktu operacyjnego JWCore
- Sposób na zachowanie kompatybilności przy wciąż ewoluującym ekosystemie Java

## Walidacja przy każdej zmianie dependencies

### Checklist przed zmianą JDK
1. Test build na nowej wersji JDK
2. Uruchomienie wszystkich testów z obecnymi flagami
3. Jeśli fail: zidentyfikuj brakujące `--add-opens` z stack trace
4. Dodaj brakujące do listy
5. Walidacja 72h stabilności
6. Aktualizuj ten ADR z nową wersją JDK i listą flag

### Checklist przed zmianą CQ
Jak wyżej, plus:
1. Review CQ release notes pod kątem nowych wymagań JVM
2. Sprawdź znane issues w GitHub CQ dla nowej wersji + Java 21

## Przykłady konfiguracji

### pom.xml (Surefire)
```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>
            --add-opens=java.base/sun.nio.ch=ALL-UNNAMED
            --add-opens=java.base/java.lang.reflect=ALL-UNNAMED
            --add-opens=java.base/java.io=ALL-UNNAMED
            --add-opens=java.base/java.nio=ALL-UNNAMED
            --add-opens=java.base/java.util=ALL-UNNAMED
            --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED
            --add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED
        </argLine>
    </configuration>
</plugin>
```

### systemd unit (execution-crypto.service)
```ini
[Unit]
Description=JWCore Execution Crypto
After=network.target

[Service]
Type=simple
User=jwcore
WorkingDirectory=/opt/jwcore
ExecStart=/usr/bin/java \
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-opens=java.base/java.nio=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED \
  --add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED \
  -jar /opt/jwcore/jwcore-execution-crypto.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

## Konsekwencje

**Pozytywne:**
- Zdefiniowana zależność runtime, nie ukryta
- Jednolita konfiguracja dev/test/prod
- Łatwa diagnostyka problemów (wiemy czego szukać)

**Negatywne:**
- Dług technologiczny — flagi to obejście hermetyzacji
- Przy każdej migracji JDK weryfikacja zgodności
- Project Panama (FFM API) docelowo zastąpi te flagi — monitoring zmian w Chronicle Queue

**Mitygacja:**
- Lista flag skonfigurowana centralnie (profil Maven property, wspólne helper w systemd)
- Dokumentacja operacyjna (README.md repo)
- Quarterly review CQ i JDK releases

## Zależności

- ADR-003 (Event Sourcing — Chronicle Queue)
- ADR-012 (Pinning CQ 5.25ea16)
- Java 21 Temurin LTS
