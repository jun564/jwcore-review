package org.jwcore.adapter.cq;

import org.jwcore.core.ports.AbstractEventJournalContractTest;
import org.jwcore.core.ports.IEventJournal;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class ChronicleQueueEventJournalContractTest extends AbstractEventJournalContractTest {

    @TempDir
    Path tempDir;

    @Override
    protected IEventJournal createJournal() {
        return new ChronicleQueueEventJournal(new ChronicleQueueJournalConfig(tempDir, "events-business-contract", "market-data-contract"));
    }
}
