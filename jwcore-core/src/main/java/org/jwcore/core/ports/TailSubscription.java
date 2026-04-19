package org.jwcore.core.ports;

public interface TailSubscription extends AutoCloseable {
    @Override
    void close();
}
