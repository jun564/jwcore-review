# Narzędzia zespołu JWCore

**Wersja 1.0 | 22.04.2026**

Inwentaryzacja dostępnych narzędzi, integracji i connectorów każdego członka zespołu. Dokument aktualizowany przy zmianach w środowisku pracy.

---

## Po co ten dokument

Przed propozycją nowego narzędzia lub integracji — sprawdź co już mamy. Zasada z Doc1 v2.0: **"zacznijmy od tego co mamy, zanim dodamy nowe"**.

Zespół nie zakłada co które narzędzie umie — sprawdzamy, dokumentujemy, korzystamy.

---

## Architekt

**Środowisko:** Komputer roboczy Windows + 3 serwery VPS.

**Dostępne narzędzia:**
- Windows (laptop roboczy)
- Monitor Dell U3219Q (31,5 cala 4K) — drugi ekran
- Telefon Android — trzeci widok (mobilny)
- Przeglądarki: Firefox + Chrome
- Dostęp SSH do wszystkich 3 serwerów (Hetzner, ForexVPS, Contabo)
- Git bash / PowerShell
- Konto Dukascopy Bank SA (KYC pending)
- Konta demo MT5 na Contabo (4 konta — TradeQuo-Server)
- Subskrypcje AI:
  - ChatGPT (dostęp do GPT i Codex)
  - Gemini
  - Claude (claude.ai)
  - Claude Code (terminalowy)

---

## Gemini — Chief Technical Architect

**Środowisko:** Gemini web app.

**Dostępne narzędzia:**
- Gemini (podstawowy tryb chat)
- **Importuj kod** — funkcja pozwalająca zaciągnąć kod z repo GitHub do kontekstu
  - ⚠️ Brak live sync — wymaga re-importu po każdej iteracji
  - Zasięg: cały folder repo lub wybrane pliki
- Deep Research — tryb dłuższej analizy
- Canvas — współdzielony dokument

**Ograniczenia:**
- Nie ma connectora do GitHub z dostępem read+write
- Nie ma dostępu do plików lokalnych Architekta
- Nie ma dostępu do serwerów

**Typowe zadania:**
- Projektowanie architektury (ADR)
- Ocena propozycji implementacyjnych
- Deep Research dla decyzji technologicznych

---

## GPT — Principal Engineer

**Środowisko:** ChatGPT web app.

**Dostępne narzędzia:**
- ChatGPT (podstawowy tryb chat)
- **Connector GitHub** — dostęp do repo przez repo+path+ref
  - Format odczytu: repo=jun564/jwcore, path=jwcore-domain/src/main/java/org/jwcore/domain/EventEnvelope.java, ref=main
  - ⚠️ NIE używać URL https:// bezpośrednio — tylko strukturalne repo+path+ref
  - ⚠️ NIE listować katalogów — czytać konkretne pliki
- Code Interpreter — Python sandbox do analiz danych
- DALL-E — generowanie obrazów (niepotrzebne w JWCore)
- Web browsing

**Ograniczenia:**
- Bez uprawnień write do repo (nie commituje)
- Limit długości pliku dla connectora GitHub

**Typowe zadania:**
- Propozycje implementacji kodu Java (jako tekst)
- Analiza istniejącego kodu w repo
- Rozwiązywanie problemów technicznych

---

## Claude — Quality Gate Coordinator

**Środowisko:** Claude.ai web app.

**Dostępne narzędzia:**
- Claude (podstawowy tryb chat)
- **Memory** — pamięć trwała między czatami (wpisy limit 30)
- **Connector GitHub** — dostęp per czat (nie cała konwersacja)
- Claude Projects — współdzielone artefakty między czatami (nie używane dla JWCore)
- Bash tool (sandboxed) — wykonywanie komend w kontenerze
- File creation (docx, md, pdf)
- Web search + web fetch
- Conversation search — przeszukiwanie poprzednich czatów

**Pamięć Claude (30 wpisów):**
- Wpisy 1-30 opisują zasady, kontekst projektu, stan prac
- Aktualizowane manualnie (replace, add, remove)
- Synchronizacja z Doc1 v2.0 (Zasady Pracy Zespołu AI)

**Typowe zadania:**
- Synteza opinii zespołu (Gemini + GPT → prompt dla Codex)
- Dokumentacja projektu (docx)
- Krytyka propozycji
- Q/A dla Architekta

---

## Codex — Implementation Agent (kod)

**Środowisko:** chatgpt.com/codex — osobny widok zadaniowy.

**Dostępne narzędzia:**
- Własne środowisko git (autonomiczne VM)
- Dostęp do repo `jun564/jwcore` (clone, branch, commit, push)
- Terminal bash w VM
- Języki: Java (główny), Python, inne
- Narzędzia build: Maven, Gradle
- `make_pr` tool — tworzenie PR po zmianach
- Prompty pole "Poproś o zmiany" lub "Opisz zadanie" — bez limitu długości

**Ograniczenia:**
- BRAK dostępu do serwerów SSH Architekta
- BRAK dostępu do plików lokalnych Architekta
- BRAK kontekstu sesji Claude — każdy prompt samowystarczalny
- Każde nowe zadanie = NOWE zadanie (nie kontynuacja poprzedniego)

**Typowe zadania:**
- Implementacja paczek Sprint (kod Java)
- Audyty kodu
- Refactor
- Dodawanie testów

**Zachowanie operacyjne (z doświadczeń 19-21.04):**
- Potrafi deklarować utworzenie PR gdy nie wypchnął — OBOWIĄZKOWO weryfikować na GitHub
- Ignoruje zakazy przy kontynuacji zadania — zawsze NOWE zadanie
- Przy audytach może improwizować zmiany Java mimo zakazu — dodawać do promptu "zatrzymaj się i zapytaj jeśli robisz coś innego niż raport"

---

## Claude Code — Implementation Agent (infrastruktura)

**Środowisko:** Terminal Claude Code na komputerze Architekta (Windows).

**Dostępne narzędzia:**
- Git + PAT (pełny dostęp read+write do repo)
- SSH do wszystkich 3 serwerów:
  - Hetzner VPS (Ubuntu)
  - ForexVPS (Windows)
  - Contabo VPS (Ubuntu po migracji)
- Dostęp do plików lokalnych w `C:\Users\...\Farma JWCore`
- Bash, PowerShell
- Maven (lokalnie i na serwerach)
- Python 3.x
- Narzędzia systemowe (grep, find, cat, itd.)

**Ograniczenia:**
- Windows Terminal ucina długie prompty wklejone bezpośrednio — dłuższe prompty w pliku (outputs), wklejać `przeczytaj plik X`
- Bez interfejsu web — tylko terminal

**Typowe zadania:**
- Walidacja build (`mvn clean verify`, `mvn jacoco:report`)
- Merge PR do main (squash merge)
- Uruchamianie testów na serwerach
- Zarządzanie plikami lokalnymi
- Git operations (push, tag, branch management)
- Backup, restart usług
- SSH operations

---

## Podsumowanie — kto co może

| Operacja | Architekt | Gemini | GPT | Claude | Codex | Claude Code |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| Odczyt repo | ✓ | ✓ (import) | ✓ (connector) | ✓ (connector) | ✓ (clone) | ✓ (clone) |
| Zapis repo | — | — | — | — | ✓ (branch+PR) | ✓ (push main) |
| Dostęp SSH | ✓ | — | — | — | — | ✓ |
| Pliki lokalne | ✓ | — | — | — | — | ✓ |
| Projektowanie arch | ✓ | ✓ | — | — | — | — |
| Implementacja | — | — | ✓ (tekst) | — | ✓ (repo) | ✓ (fixy) |
| Jakość/dokumentacja | — | — | — | ✓ | — | — |
| Build/test | — | — | — | — | ✓ | ✓ |
| Decyzje projektu | ✓ | — | — | — | — | — |

✓ = może, — = nie może

---

## Aktualizacja dokumentu

Ten dokument jest aktualizowany gdy:
- Zmienia się środowisko pracy któregoś członka zespołu
- Dodawany jest nowy narzędzie/integracja/connector
- Odkrywane są nieznane wcześniej możliwości istniejących narzędzi

Claude jako Quality Gate monitoruje aktualność i zgłasza Architektowi propozycję aktualizacji przy końcu sesji.

---

**Wersja:** 1.0
**Data:** 22.04.2026
