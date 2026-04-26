package org.jwcore.execution.common.broker;

import org.jwcore.domain.OrderIntent;

import java.math.BigDecimal;

/**
 * Service Provider Interface dla brokera w pipeline egzekucji JWCore.
 * Unifikuje zduplikowane interfejsy z execution-crypto i execution-forex (4D1).
 *
 * UWAGA: Import OrderIntent skopiowac DOKLADNIE z istniejacych
 * jwcore-execution-{crypto,forex}/.../broker/BrokerSession.java
 * Nie zgaduj pakietu — uzyj tego co jest w repo.
 */
public interface BrokerSession {
    boolean isConnected();
    String submit(OrderIntent orderIntent);
    BigDecimal currentMarginLevel();
    BigDecimal currentFreeMargin();
    BigDecimal currentEquity();

    /**
     * Rejestracja listenera dla eventow generowanych przez brokera.
     * Hybrydowy model komunikacji (4D1, Opcja C): broker pchnie event do listenera,
     * listener zapisze do journala, reszta systemu czyta z journala (pull).
     *
     * @param listener implementacja listenera, NIE moze byc null
     * @throws NullPointerException gdy listener == null
     */
    void registerListener(BrokerEventListener listener);
}
