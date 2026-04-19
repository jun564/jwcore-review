package org.jwcore.core.shutdown;

@FunctionalInterface
public interface ShutdownHookRegistrar {
    void register(Thread hook);
}
