package org.jwcore.core.shutdown;

public interface GracefulShutdownParticipant {
    String name();

    void flush() throws Exception;

    void snapshot() throws Exception;
}
