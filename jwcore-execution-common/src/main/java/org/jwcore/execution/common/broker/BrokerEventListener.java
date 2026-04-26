package org.jwcore.execution.common.broker;

import org.jwcore.domain.EventEnvelope;

/**
 * Listener eventow emitowanych przez BrokerSession (hybryda C, 4D1).
 *
 * UWAGA ARCHITEKTONICZNA (TWARDY ZAKAZ):
 * Implementacje TYLKO zapisuja event do journala lub kontrolowanej listy testowej.
 * NIE WOLNO wolac RiskCoordinatorEngine, ExecutionRuntime ani innych komponentow
 * biznesowych z poziomu listenera. Naruszenie = drugi kanal eventowy obok journala
 * = niedeterminizm + naruszenie ADR-017 (journal jako single source of truth).
 */
public interface BrokerEventListener {
    void onBrokerEvent(EventEnvelope envelope);
}
