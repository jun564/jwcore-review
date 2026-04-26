package org.jwcore.execution.common.broker;

import org.jwcore.domain.OrderIntent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StubBrokerSession implements BrokerSession {
    private final List<OrderIntent> submitted = new ArrayList<>();
    private final List<String> brokerOrderIds = new ArrayList<>();
    private final String orderIdPrefix;
    private boolean connected = true;
    private BigDecimal marginLevel = new BigDecimal("100");
    private BigDecimal freeMargin = new BigDecimal("1000");
    private BigDecimal equity = new BigDecimal("1000");
    private BrokerEventListener listener;  // 4D1 migration: may be null until registerListener is called

    public StubBrokerSession() {
        this("STUB-ORD-");
    }

    public StubBrokerSession(final String orderIdPrefix) {
        this.orderIdPrefix = Objects.requireNonNull(orderIdPrefix, "orderIdPrefix cannot be null");
    }

    @Override public boolean isConnected() { return connected; }
    @Override public String submit(final OrderIntent orderIntent) {
        submitted.add(orderIntent);
        final String brokerOrderId = orderIdPrefix + UUID.randomUUID();
        brokerOrderIds.add(brokerOrderId);
        return brokerOrderId;
    }
    @Override public BigDecimal currentMarginLevel() { return marginLevel; }
    @Override public BigDecimal currentFreeMargin() { return freeMargin; }
    @Override public BigDecimal currentEquity() { return equity; }

    @Override
    public void registerListener(final BrokerEventListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener cannot be null");
    }

    public List<OrderIntent> submitted() { return List.copyOf(submitted); }
    public List<String> brokerOrderIds() { return List.copyOf(brokerOrderIds); }
    public void setConnected(final boolean connected) { this.connected = connected; }
    public void setMarginSnapshot(final BigDecimal marginLevel, final BigDecimal freeMargin, final BigDecimal equity) {
        this.marginLevel = marginLevel; this.freeMargin = freeMargin; this.equity = equity;
    }
}
