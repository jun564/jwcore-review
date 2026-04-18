# Prompty Sesji Claude 12 — JWCore (18.04.2026)

Kolejność chronologiczna. Każdy prompt ma nagłówek z datą i kontekstem.

---

## Prompt 1 — Sprint 2 iteracja naprawcza (po blokerze CQ)

**Kontekst:** Sprint 2 v1 zawiódł (BUILD FAILURE w jwcore-adapter-cq). Zespół wybrał Opcję A (naprawa w miejscu, nie odkładanie CQ).

```
Odpowiadaj wyłącznie po polsku / Respond in Polish only.

Sprint 2 — iteracja naprawcza. Gemini i Claude zatwierdzili plan, masz zielone światło.

ZADANIA:

1. pom.xml — konfiguracja maven-surefire-plugin (i failsafe jeśli używany):
   <argLine> z wymaganymi flagami:
   --add-opens=java.base/sun.nio.ch=ALL-UNNAMED
   --add-opens=java.base/java.lang.reflect=ALL-UNNAMED
   --add-opens=java.base/java.io=ALL-UNNAMED
   --add-opens=java.base/java.nio=ALL-UNNAMED
   --add-opens=java.base/java.util=ALL-UNNAMED
   (plus wszystkie inne których wymaga Chronicle Queue wg ich dokumentacji)

2. Uruchom mvn clean verify z nową konfiguracją.

3. Jeśli Chronicle Queue 2026.2 dalej nie przechodzi — ZRÓB DOWNGRADE do stabilnej gałęzi 5.25.x.

4. Branch coverage — twardy wymóg ≥80% w jwcore-domain i jwcore-core.

5. RAPORT KOŃCOWY z flagami JVM wymaganymi w runtime (dług operacyjny dla systemd).

Startuj.
```

**Wynik:** GPT dostarczył jwcore-sprint2-v2.zip. Code uruchomił na jwcore-live-01: BUILD SUCCESS, 77 testów, coverage ≥80% we wszystkich modułach.

---

## Prompt 2 — Sprint 3 pełny (pierwotny, przed podziałem)

**Kontekst:** Po zatwierdzeniu pakietu ADR (008-013 + rozszerzenie 006). Blocker Sprintu 3 zdjęty.

```
Odpowiadaj wyłącznie po polsku / Respond in Polish only.

SPRINT 3 — JWCore. Pakiet ADR-008 do ADR-013 (plus rozszerzenie ADR-006) został zatwierdzony przez zespół. Sprint 3 wprowadza system decyzyjny: execution adaptery, risk coordinator, reconciliation.

===== ZAKRES =====

1. Trzy procesy JVM: jwcore-execution-crypto, jwcore-execution-forex, jwcore-risk-coordinator
2. Wspólny moduł jwcore-execution-common
3. Pełna implementacja jwcore-adapter-jforex (IStrategy, order send, terminal events, reconnect)
4. Risk Engine — lokalne (Poziom 1-2) + globalne (Poziom 3 cross-account)
5. Tryby awaryjne RUN/SAFE/HALT/KILL (hierarchia KILL>HALT>SAFE>RUN)
6. Sole State Rebuilder + Reconciliation Engine
7. OrderTimeoutEvent implementacja
8. Graceful Shutdown (bounded lifecycle executorów)

===== TESTY =====
≥80% branch coverage we wszystkich nowych modułach.

===== CZEGO NIE ROBIMY =====
GUI, strategie, live Dukascopy, FIX Protocol.

===== DELIVERABLE =====
1. jwcore-sprint3.zip
2. README + RAPORT (mvn clean verify + coverage)
3. Przykładowe systemd unit files

Startuj.
```

**Wynik:** GPT dostarczył fragment kodu inline zamiast paczki. Nie spełnił kontraktu. Zespół (Claude + GPT + Gemini) zdecydował podzielić Sprint 3 na 3 iteracje.

---

## Prompt 3 — Sprint 3.1 (iteracyjnie, Opcja A)

**Kontekst:** Po niepowodzeniu pełnego Sprintu 3. Akceptacja Opcji A (iteracyjnie).

```
Odpowiadaj wyłącznie po polsku / Respond in Polish only.

Akceptuję Opcję A — iteracyjny Sprint 3 (3.1 → 3.2 → 3.3).

WARUNKI AKCEPTACJI:
1. Sprint 3 zamknięty dopiero po 3.1 + 3.2 + 3.3
2. Każda iteracja: kompletna paczka ZIP + mvn clean verify BUILD SUCCESS + coverage ≥80% branch
3. docs/sprint3-backlog.md z planem 3 iteracji + listą długu
4. Quality Gate review po każdej iteracji

ZAKRES SPRINT 3.1:
A) jwcore-execution-common (ExecutionState, Resolver, OrderTimeoutTracker, IntentRegistry, eventy)
B) jwcore-execution-crypto (ExecutionRuntime z tickCycle, main + graceful shutdown, stub BrokerSession)
C) jwcore-execution-forex (analogicznie)
D) jwcore-risk-coordinator (szkielet + struktura tailera CQ)

TESTY (≥80% branch coverage, obowiązkowo):
- ExecutionStateTest, ExecutionStateResolverTest, OrderTimeoutTrackerTest, IntentRegistryTest
- StateMachineTest, ExecutionRuntimeTest

ZAKRES 3.2: JForex pełny, reconciliation, state rebuilder, market-data ingestion, testy na DEMO
ZAKRES 3.3: Risk cross-account, Margin Monitor data, systemd, test 72h stabilności

Startuj 3.1 teraz.
```

**Wynik:** GPT dostarczył "wersja 0 foundation" — podzbiór zakresu 3.1 bez ExecutionRuntime, testów, coverage. Sam przyznał że nie spełnia warunków.

---

## Prompt 4 — Twardy reset (przed decyzją o zmianie czatu)

**Kontekst:** GPT dostarcza poniżej kontraktu. Test hipotezy: zmęczenie czatu czy świadoma strategia minimalizmu.

```
[Pełny prompt w tekście sesji — zbyt długi do powtórzenia tutaj]
Najważniejsze punkty:
- Nie wyślę kolejnej niekompletnej paczki
- Nie pytaj "A czy B" — dowozisz zakres
- Nie dziel 3.1 na 3.1a/3.1b
- Jeśli coś niemożliwe — konkretne uzasadnienie techniczne przed startem
- Jeśli znów dostarczysz niekompletne → zmiana czatu GPT
```

**Wynik:** GPT odpowiedział uczciwie — nie ma Mavena, nie może wykonać mvn clean verify, nie może samodzielnie potwierdzić kontraktu. Zaproponował Wariant 1 (GPT kod → Code build → feedback).

**Decyzja Architekta:** koniec sesji, jutro nowy czat GPT z Wariantem 1 i pełnym onboardingiem.

---

## Prompt 5 — Onboarding nowego czatu GPT (do użycia jutro)

**Pełny dokument:** `docs/GPT_ONBOARDING.md`

**Instrukcja użycia:**
1. Otworzyć nowy czat u GPT
2. Skopiować w całości treść z sekcji "Pierwszy prompt do nowego czatu GPT" (między podwójnymi poziomymi liniami)
3. Wkleić jako pierwsza wiadomość
4. GPT potwierdza rolę, zaczyna Sprint 3.1
5. Wariant wykonawczy 1: GPT pisze kod → Code buduje → feedback loop

---

## Lessons learned sesji Claude 12

### Co zadziałało

1. **Zasada bezpiecznej kolejności Code** — zapisana do pamięci po incydencie z ubuntu lock. Zapobiegła powtórce przy konfiguracji jwcore-live-01.
2. **Iteracyjna naprawa Sprint 2** — wybór Opcji A (nie odkładanie CQ) okazał się słuszny. Gemini miał rację co do diagnostyki, GPT słusznie dołożył ostrzeżenie o runtime.
3. **Quality Gate Claude** — wyłapanie że `ExecutionState`, `ExecutionStateResolver`, `OrderTimeoutTracker` były używane w testach baseline Sprint 3 ale nie zaimplementowane.
4. **Race condition w tail() naprawiony przez Code** — wartość dodana spoza kontraktu, ale właściwie.

### Co nie zadziałało

1. **Sprint 3 pełny w jednej turze** — GPT nie dał rady. Nauka: decyzje o podziale sprintów podejmować przed startem, nie retrospektywnie.
2. **GPT oferowanie "A/B" po niekompletnej dostawie** — zasada zespołu (nie przytakiwanie) działa w dwie strony. GPT przytakiwał własnej wygodzie zamiast standardom.
3. **Brak jasnego rozróżnienia mocy środowisk** — nie wiedzieliśmy że GPT nie ma Mavena. To powinno być w onboardingu wcześniej.

### Zmiany w zasadach zespołu

Do rozważenia na przyszłe sesje (jeszcze niezapisane):
- Dopisek do Zasady 5: "Gdy dostarczane deliverable nie spełnia warunków akceptacji — nie oferuj Architektowi wyboru A/B, dowozisz zgodnie z zakresem"
- Weryfikacja narzędzi środowiska na starcie każdego nowego czatu GPT
