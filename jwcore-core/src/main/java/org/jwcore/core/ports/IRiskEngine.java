package org.jwcore.core.ports;

import org.jwcore.domain.OrderIntent;

public interface IRiskEngine {
    boolean validate(OrderIntent orderIntent);
}
