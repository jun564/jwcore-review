package org.jwcore.core.ports;

import org.jwcore.domain.Bar;
import org.jwcore.domain.Tick;

public interface IStrategy {
    void onTick(Tick tick);
    void onBar(Bar bar);
}
