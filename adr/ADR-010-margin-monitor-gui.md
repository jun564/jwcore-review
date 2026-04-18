# ADR-010 — Margin Monitor w GUI

**Data:** 18.04.2026
**Status:** Zatwierdzony (Gemini + GPT + Claude + Architekt)
**Kontekst:** Pierwotna propozycja GPT zawierała automatyczny Margin Watchdog likwidujący pozycje przy spadku margin level. Architekt odrzucił automat — broker sam likwiduje (decyzja biznesowa: "broker likwiduje po 1 pozycji, nie my wszystko naraz"). Potrzebna jest widoczność marginów dla Architekta.

## Decyzja

**Margin Monitor jest komponentem GUI informacyjnym, nie automatem decyzyjnym.**

## Wyświetlanie (decyzja Architekta 18.04.2026)

**Dwa rachunki osobno, bez sumy zbiorczej:**

```
Krypto:    margin 67%  │  free 4 320 EUR  │  equity 12 500 EUR
Forex:     margin 45%  │  free 1 850 EUR  │  equity 8 200 EUR
```

**Brak zbiorczej sumy** — uśredniona liczba jest myląca i ukrywa sytuację gdy jeden rachunek jest blisko margin call.

## Alerty

| Margin level | Kolor | Akcja |
|---|---|---|
| > 100% | zielony | OK |
| 50-100% | żółty | ostrzeżenie wyświetlane |
| 30-50% | pomarańczowy | ostrzeżenie + notyfikacja Telegram |
| ≤ 30% | czerwony | stan krytyczny + Telegram + migająca ramka w GUI |

Progi konfigurowalne per rachunek w `risk-coordinator.properties`.

## Zakres funkcjonalny

**Co Margin Monitor robi:**
- Wyświetla aktualne wartości margin/free/equity per rachunek
- Pokazuje historię margin level (wykres godzinowy/dzienny)
- Emituje alerty Telegram przy przekroczeniu progów
- Zapisuje log przekroczeń do dedykowanej sekcji CQ (audit trail)

**Czego Margin Monitor NIE robi (wyraźny zapis):**
- **Żadnego automatycznego zamykania pozycji**
- Żadnego blokowania nowych zleceń (to robi Risk Coordinator + Execution państwo maszynowe ADR-011)
- Żadnej komunikacji z brokerem (tylko wyświetlanie danych)

## Decyzja operacyjna należy do Architekta

Po alercie Architekt decyduje:
1. **Zasilić konto** — przelew z rachunku głównego Dukascopy
2. **Zamknąć manualnie pozycje** — przez GUI lub platformę brokerską
3. **Poczekać** — broker sam zamknie gdy margin spadnie do poziomu stop-out
4. **Wprowadzić HALT** — przez komendę do Risk Coordinator (zablokuje nowe zlecenia, pozycje zostaną)

Automat **nigdy** nie podejmuje decyzji za Architekta w tej kwestii.

## Źródło danych

Event sourcing — Execution per rachunek emituje `MarginUpdateEvent`:
- Co 5 sekund (regularnie, niezależnie od aktywności)
- Dodatkowo przy każdej zmianie marginu > 2%
- Przy otwarciu/zamknięciu pozycji

GUI tailuje `events-business` CQ i renderuje najnowsze wartości per `account_id`.

## Struktura MarginUpdateEvent

```java
MarginUpdateEvent {
    String account_id;          // "crypto" | "forex"
    double margin_level_pct;    // np. 67.5
    double free_margin;         // waluta rachunku
    double equity;              // waluta rachunku
    double used_margin;         // waluta rachunku
    String account_currency;    // "EUR" | "USD"
    Instant snapshot_at;        // timestamp z brokera (nie lokalny)
    EventEnvelope envelope;
}
```

## Konsekwencje

**Pozytywne:**
- Transparentność stanu dla Architekta
- Brak ryzyka automatu podejmującego złe decyzje przy flash crash
- Audit trail wszystkich przekroczeń
- Zgodność z filozofią "człowiek decyduje o kapitale"

**Negatywne:**
- Wymaga aktywnego monitorowania przez Architekta
- Reakcja ludzka wolniejsza niż automat (minuty vs sekundy)
- Brak ochrony gdy Architekt jest niedostępny

**Mitygacja:**
- Notyfikacje Telegram wieloprogowe (żółty/pomarańczowy/czerwony)
- Broker stop-out jako ostateczny safety net (nie polegamy na Architekcie w krytycznym momencie)
- Risk Coordinator może wprowadzić HALT autonomicznie (ADR-011) — to chroni kapitał różnymi ścieżkami

## Zależności

- ADR-006 (Event Envelope)
- ADR-011 (stany awaryjne per rachunek)
- Sprint 5 — implementacja GUI
- Sprint 3.3 — MarginUpdateEvent emission
