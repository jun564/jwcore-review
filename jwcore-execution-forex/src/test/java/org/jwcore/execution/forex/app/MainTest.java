package org.jwcore.execution.forex.app;

import org.jwcore.execution.forex.app.support.InMemoryEventJournal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MainTest {
    @Test
    void shouldBootstrapApplication() throws Exception {
        final Main.Application application = Main.bootstrap(new InMemoryEventJournal());
        assertNotNull(application.runtime());
        assertNotNull(application.config());
        application.coordinator().execute();
    }
}
