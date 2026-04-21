package org.jwcore.execution.common.events;

import org.jwcore.domain.EventEnvelope;

import java.math.BigDecimal;
import java.util.Objects;

public record MarginUpdateEvent(String accountId, BigDecimal marginLevel, BigDecimal freeMargin, BigDecimal equity, EventEnvelope envelope) {
    public MarginUpdateEvent {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(marginLevel, "marginLevel cannot be null");
        Objects.requireNonNull(freeMargin, "freeMargin cannot be null");
        Objects.requireNonNull(equity, "equity cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");
    }
}
