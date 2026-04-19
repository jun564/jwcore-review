package org.jwcore.adapter.cq;

import java.nio.file.Path;
import java.util.Objects;

public record ChronicleQueueJournalConfig(Path baseDirectory, String businessQueueName, String marketDataQueueName) {
    public ChronicleQueueJournalConfig {
        Objects.requireNonNull(baseDirectory, "baseDirectory cannot be null");
        Objects.requireNonNull(businessQueueName, "businessQueueName cannot be null");
        Objects.requireNonNull(marketDataQueueName, "marketDataQueueName cannot be null");
    }

    public Path businessQueuePath() {
        return baseDirectory.resolve(businessQueueName);
    }

    public Path marketDataQueuePath() {
        return baseDirectory.resolve(marketDataQueueName);
    }
}
