package org.jwcore.riskcoordinator.app;

import org.jwcore.riskcoordinator.app.support.InMemoryEventJournal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MainTest {
    @Test
    void shouldBootstrapApplication() throws Exception {
        final Main.Application application = Main.bootstrap(new InMemoryEventJournal(), new InMemoryEventJournal());
        assertNotNull(application.engine());
        assertNotNull(application.tailer());
        assertNotNull(application.config());
        application.coordinator().execute();
    }
}
