package org.jwcore.core.ports;

import org.jwcore.domain.Instrument;

public interface IMarketDataProvider {
    void subscribe(Instrument instrument);
    void unsubscribe(Instrument instrument);
}
