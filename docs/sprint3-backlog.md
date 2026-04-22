# Sprint 3 — Backlog i stan paczek

**Wersja:** 1.3 (22.04.2026 wieczór, po zamknięciu Paczki 4A)
**Repo:** jwcore (prywatne) / jwcore-review (publiczne)
**Branch bazowy:** main

---

## Stan paczek Sprint 3.1

| Paczka | Opis | Status | Merge |
|--------|------|--------|-------|
| 3.1.1 | Audit Sprint 3.1 | ZAMKNIĘTA | main |
| 3.1.2 | Domain lifecycle events + RejectReason | ZAMKNIĘTA | main |

## Stan paczek Sprint 3.2

| Paczka | Opis | Status | PR | Commit merge |
|--------|------|--------|-----|--------------|
| 1 | OrderEventEnvelope + BinaryCodec | ZAMKNIĘTA | — | main |
| 2 | ChronicleQueueEventJournal MVP | ZAMKNIĘTA | — | main |
| 3A | Domain lifecycle events | ZAMKNIĘTA | #12 | 6d6f3a4 |
| 3B | ExposureLedger + SAFE MVP + deterministyczny idempotency key | ZAMKNIĘTA | #13 | 6f1075e |
| 3C | Sequence API + RiskCoordinatorTailer offset | ZAMKNIĘTA | #15 | 1d065b6 |
| 3D | Rename timestampMono → sequenceNumber | ZAMKNIĘTA | #16 | 85f1837 |
| 4A | ExposureLedger pełna pozycja finansowa + lifecycle events + OrderTimeoutMonitor sync | ZAMKNIĘTA | #17 | d39e7c1 |

## Backlog Sprint 3.2 — do realizacji

### Paczka 4B — reconnect/reconcile + dokończenie modelu ryzyka

**Priorytet: BLOKUJE PoC JForex**

Zakres:
- DŁUG-314: reconnect/reconcile w BrokerSession (warunek PoC JForex)
- Przepisanie 5 testów `@Disabled` w `RiskCoordinatorEngineTest` pod nowy model exposure:
  - `shouldAddExposureOnOrderSubmitted`
  - `shouldSubtractExposureOnOrderFilled`
  - `shouldSubtractExposureOnOrderCanceled`
  - `shouldNotChangeExposureOnOrderUnknown`
  - `shouldFullLifecycleSequence`
  - Nowy model wymaga sekwencji `intent → fill` przed oczekiwaniem exposure; `submitted` sam nie generuje już pozycji w ledgerze.
- Polityka reconcile po reconnect (wykracza poza fail-fast `IllegalStateException` z 4A)

### Paczka 4C — error isolation + SAFE/HALT alerting

Zakres:
- ADR-017 error isolation (pełne wdrożenie — Etap 2+)
- A) Eskalacja SAFE→HALT przy powtórzonym OrderUnknown (Wariant D Architekta)
- B) Panel + Telegram alerting SAFE/HALT (ADR-010)
- C) Manualny reset SAFE→RUN (RiskStateResetCommand)

### Paczka 4D — Advanced Stub Broker

**Priorytet: warunek przed PoC JForex**

Zakres:
- H) Advanced Stub Broker — simulator egzekucji, timeouty, reject, partial fill, stan konta
- Testy integracyjne execution runtime ↔ stub broker

### Paczka 5 — PoC JForex

**Wymaga zamknięcia 4B i 4D**

Zakres:
- Adapter JForex SDK (Dukascopy demo)
- Etap 1 PoC na koncie demo (obowiązkowo)
- Mapowanie eventów brokera → domena JWCore

## Długi techniczne — aktualny status

| ID | Temat | Status | Blokuje PoC? |
|----|-------|--------|--------------|
| DŁUG-301 | IntentPhase.canTransitionTo() niekompletny | OTWARTY | NIE |
| DŁUG-302 | CanonicalId walidacja formatów | OTWARTY (niski) | NIE |
| DŁUG-303 | EventEnvelope serializacja versioning | OTWARTY | NIE |
| DŁUG-304 | IdempotencyKeys bez salt | OTWARTY (niski) | NIE |
| DŁUG-305 | OrderTimeoutMonitor bez synchronizacji | ZAMKNIĘTY (4A w main) | — |
| DŁUG-306 | CQ retention policy | OTWARTY | NIE |
| DŁUG-307 | EventEmitter bez circuit breaker | OTWARTY | NIE |
| DŁUG-308 | IntentRegistry bez TTL | OTWARTY | NIE |
| DŁUG-309 | Pełna pozycja finansowa ExposureLedger | ZAMKNIĘTY (4A w main) | — |
| DŁUG-310 | OrderRejected bez size w payload | OTWARTY | NIE |
| DŁUG-311 | RiskCoordinatorTailer offset | ZAMKNIĘTY (3C w main) | — |
| DŁUG-312 | Unifikacja 7 kopii InMemoryEventJournal | OTWARTY | NIE |
| DŁUG-313 | Rename timestampMono → sequenceNumber | ZAMKNIĘTY (3D w main) | — |
| DŁUG-314 | Reconnect/reconcile w BrokerSession | OTWARTY (wysoki) | TAK |
| DŁUG-315 | TokenBucket rate limiter | OTWARTY | NIE |
| DŁUG-316 | Workflow sync-to-review | ZAMKNIĘTY (22.04) | — |
| DŁUG-317 | readAfterSequence O(n) → O(log n) indexing | OTWARTY (nowy, z 3C) | NIE |
| DŁUG-318 | Przepisać 5 @Disabled testów RiskCoordinatorEngine pod nowy model | OTWARTY (nowy, z 4A) | NIE |

## Poprzednie priorytety domknięte 22.04.2026

- Pakiet dokumentacji v2.0 → wypchnięte do main (commit 4633700)
- Incydent bezpieczeństwa PAT → PAT odwołany, nowy w Credential Manager, klon repo w stałej lokalizacji
- DŁUG-316 (workflow sync-to-review) → naprawione, sekret JWCORE_REVIEW_TOKEN zaktualizowany
- Paczka 3C v2 (sequence API z AtomicLong) → merge
- Paczka 3D (rename) → merge
- Paczka 4A (ExposureLedger + lifecycle + OrderTimeoutMonitor sync) → merge

## Zasady pracy z backlogiem

1. Każda paczka w Sprint 3.2 powinna zamknąć przynajmniej jeden dług lub dodać wymaganą funkcjonalność dla PoC JForex.
2. Kolejność realizacji według blokerów PoC: 4B → 4D → 5 (PoC JForex).
3. Paczka 4C (error isolation + alerting) może iść równolegle.
4. `@Disabled` testy to dług oznaczony jasno w kodzie — nie mogą być zignorowane.
