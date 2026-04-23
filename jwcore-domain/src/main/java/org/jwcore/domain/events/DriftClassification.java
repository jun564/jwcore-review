package org.jwcore.domain.events;

public enum DriftClassification {
    NONE,
    MINOR,
    MAJOR,
    FATAL;

    public boolean isAcceptable() {
        return this == NONE || this == MINOR;
    }
}
