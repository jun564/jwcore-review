package org.jwcore.execution.forex.broker;

import org.jwcore.domain.OrderIntent;

import java.math.BigDecimal;

public interface BrokerSession {
    boolean isConnected();
    String submit(OrderIntent orderIntent);
    BigDecimal currentMarginLevel();
    BigDecimal currentFreeMargin();
    BigDecimal currentEquity();
}
