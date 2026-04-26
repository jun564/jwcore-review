# Sprint 3 — Backlog i stan paczek

**Wersja:** 1.4 (26.04.2026, po zamknięciu Paczek 4B1, 4B2, 4C1, 4C2/A, 4C2/B, 4C2/C)
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
| 4B1 | BrokerSession reconnect/reconcile + BrokerReconcileEvent | ZAMKNIĘTA | #18 | 653c505 |
| 4B2 | Przepisanie 5 testów @Disabled + reverse-position pod nowy model | ZAMKNIĘTA | #19 | ab162ab |
| 4C1 | SAFE→HALT escalation + RiskStateResetCommand + ITimeProvider | ZAMKNIĘTA | #21 | 9727ef5 |
| 4C2/A | AlertEvent + adapter-alerting (Telegram + JUL panel) | ZAMKNIĘTA | #22 | 02b8d09 |
| 4C2/B | ProcessingFailureEmitter + risk-coordinator catch/emit (ADR-017 Faza 1) | ZAMKNIĘTA | #23 | 5149d50 |
| 4C2/C | Auto-eskalacja SAFE + atomicity copy-before-commit (ADR-017 Faza 2) | ZAMKNIĘTA | #24 | 818f1f3 |

## Backlog Sprint 3.2 — do realizacji

### Paczka 4D — Advanced Stub Broker

**Priorytet: warunek przed PoC JForex**

Zakres:
- H) Advanced Stub Broker — simulator egzekucji, timeouty, reject, partial fill, stan konta
- Testy integracyjne execution runtime ↔ stub broker

### Paczka 5 — PoC JForex

**Wymaga zamknięcia 4D**

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
| DŁUG-314 | Reconnect/reconcile w BrokerSession | ZAMKNIĘTY (4B1 w main, PR #18) | — |
| DŁUG-315 | TokenBucket rate limiter | OTWARTY | NIE |
| DŁUG-316 | Workflow sync-to-review | ZAMKNIĘTY (22.04) | — |
| DŁUG-317 | readAfterSequence O(n) → O(log n) indexing | OTWARTY (nowy, z 3C) | NIE |
| DŁUG-318 | Przepisać 5 @Disabled testów RiskCoordinatorEngine pod nowy model | ZAMKNIĘTY (4B2 w main, PR #19) | — |

## Domknięte 22.04–25.04.2026

- Pakiet dokumentacji v2.0 → wypchnięte do main (commit 4633700)
- Incydent bezpieczeństwa PAT → PAT odwołany, nowy w Credential Manager, klon repo w stałej lokalizacji
- DŁUG-316 (workflow sync-to-review) → naprawione, sekret JWCORE_REVIEW_TOKEN zaktualizowany
- Paczka 3C v2 (sequence API z AtomicLong) → merge
- Paczka 3D (rename) → merge
- Paczka 4A (ExposureLedger + lifecycle + OrderTimeoutMonitor sync) → merge 22.04
- Paczka 4B1 (DŁUG-314 reconnect/reconcile) → merge 23.04
- Paczka 4B2 (DŁUG-318 5 testów @Disabled + reverse-position) → merge 23.04
- Paczka 4C1 (SAFE→HALT escalation + reset command) → merge
- Paczka 4C2/A (AlertEvent + adapter-alerting) → merge
- Paczka 4C2/B (ProcessingFailureEmitter, ADR-017 Faza 1) → merge 24.04
- Paczka 4C2/C (ADR-017 Faza 2 auto-eskalacja) → merge 25.04
- Zasada 36 (rozbicie paczek) i Zasada 37 (system autonomiczny) — dodane do ZASADY_WSPOLPRACY

## Zasady pracy z backlogiem

1. Każda paczka w Sprint 3.2 powinna zamknąć przynajmniej jeden dług lub dodać wymaganą funkcjonalność dla PoC JForex.
2. Kolejność realizacji: 4D (Advanced Stub Broker) → 5 (PoC JForex Etap 1 demo). 4B i 4C zamknięte.
3. `@Disabled` testy to dług oznaczony jasno w kodzie — nie mogą być zignorowane.
