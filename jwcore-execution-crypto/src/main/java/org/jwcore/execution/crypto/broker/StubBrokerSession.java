package org.jwcore.execution.crypto.broker;

import org.jwcore.domain.OrderIntent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StubBrokerSession implements BrokerSession {
    private final List<OrderIntent> submitted = new ArrayList<>();
    private final List<String> brokerOrderIds = new ArrayList<>();
    private boolean connected = true;
    private BigDecimal marginLevel = new BigDecimal("100");
    private BigDecimal freeMargin = new BigDecimal("1000");
    private BigDecimal equity = new BigDecimal("1000");

    @Override public boolean isConnected() { return connected; }
    @Override public String submit(final OrderIntent orderIntent) {
        submitted.add(orderIntent);
        final String brokerOrderId = "CRYPTO-ORD-" + UUID.randomUUID();
        brokerOrderIds.add(brokerOrderId);
        return brokerOrderId;
    }
    @Override public BigDecimal currentMarginLevel() { return marginLevel; }
    @Override public BigDecimal currentFreeMargin() { return freeMargin; }
    @Override public BigDecimal currentEquity() { return equity; }

    public List<OrderIntent> submitted() { return List.copyOf(submitted); }
    public List<String> brokerOrderIds() { return List.copyOf(brokerOrderIds); }
    public void setConnected(final boolean connected) { this.connected = connected; }
    public void setMarginSnapshot(final BigDecimal marginLevel, final BigDecimal freeMargin, final BigDecimal equity) {
        this.marginLevel = marginLevel; this.freeMargin = freeMargin; this.equity = equity;
    }
}
