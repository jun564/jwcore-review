package org.jwcore.execution.crypto.broker;

import org.jwcore.domain.OrderIntent;

import java.math.BigDecimal;

public interface BrokerSession {
    boolean isConnected();
    void submit(OrderIntent orderIntent);
    BigDecimal currentMarginLevel();
    BigDecimal currentFreeMargin();
    BigDecimal currentEquity();
}
