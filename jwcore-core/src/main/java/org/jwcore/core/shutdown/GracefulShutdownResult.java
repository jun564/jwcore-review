package org.jwcore.core.shutdown;

import java.util.List;

public record GracefulShutdownResult(boolean completed, boolean timedOut, List<String> participantOrder) {
}
