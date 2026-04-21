package org.jwcore.core.ports;

import org.jwcore.domain.CanonicalId;

public interface IVirtualAccount {
    CanonicalId canonicalId();
    double allocatedCapital();
}
