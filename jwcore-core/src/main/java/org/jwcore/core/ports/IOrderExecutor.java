package org.jwcore.core.ports;

import org.jwcore.domain.OrderIntent;

public interface IOrderExecutor {
    void submit(OrderIntent orderIntent);
}
