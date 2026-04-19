package org.jwcore.core.shutdown;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GracefulShutdownCoordinatorTest {

    @Test
    void shouldExecuteFlushAndSnapshotInOrder() {
        List<String> actions = new ArrayList<>();
        GracefulShutdownCoordinator coordinator = new GracefulShutdownCoordinator(Duration.ofSeconds(1), hook -> { });
        coordinator.register(new GracefulShutdownParticipant() {
            @Override public String name() { return "journal"; }
            @Override public void flush() { actions.add("flush"); }
            @Override public void snapshot() { actions.add("snapshot"); }
        });
        GracefulShutdownResult result = coordinator.execute();
        assertTrue(result.completed());
        assertFalse(result.timedOut());
        assertEquals(List.of("flush", "snapshot"), actions);
        assertEquals(List.of("journal"), result.participantOrder());
    }

    @Test
    void shouldInstallHookOnlyOnce() {
        List<Thread> hooks = new ArrayList<>();
        GracefulShutdownCoordinator coordinator = new GracefulShutdownCoordinator(Duration.ofSeconds(1), hooks::add);
        coordinator.install();
        coordinator.install();
        assertEquals(1, hooks.size());
    }

    @Test
    void shouldRejectInvalidConstructorArguments() {
        assertThrows(NullPointerException.class, () -> new GracefulShutdownCoordinator(null, hook -> { }));
        assertThrows(NullPointerException.class, () -> new GracefulShutdownCoordinator(Duration.ofSeconds(1), null));
        assertThrows(IllegalArgumentException.class, () -> new GracefulShutdownCoordinator(Duration.ZERO, hook -> { }));
    }

    @Test
    void shouldRejectNullParticipant() {
        GracefulShutdownCoordinator coordinator = new GracefulShutdownCoordinator(Duration.ofSeconds(1), hook -> { });
        assertThrows(NullPointerException.class, () -> coordinator.register(null));
    }

    @Test
    void shouldReturnFailureWhenParticipantThrows() {
        GracefulShutdownCoordinator coordinator = new GracefulShutdownCoordinator(Duration.ofSeconds(1), hook -> { });
        coordinator.register(new GracefulShutdownParticipant() {
            @Override public String name() { return "broken"; }
            @Override public void flush() { throw new IllegalStateException("boom"); }
            @Override public void snapshot() { }
        });
        GracefulShutdownResult result = coordinator.execute();
        assertFalse(result.completed());
        assertFalse(result.timedOut());
        assertEquals(List.of("broken"), result.participantOrder());
    }

    @Test
    void shouldTimeoutWhenParticipantBlocks() {
        GracefulShutdownCoordinator coordinator = new GracefulShutdownCoordinator(Duration.ofMillis(10), hook -> { });
        coordinator.register(new GracefulShutdownParticipant() {
            @Override public String name() { return "slow"; }
            @Override public void flush() throws Exception { Thread.sleep(100); }
            @Override public void snapshot() { }
        });
        GracefulShutdownResult result = coordinator.execute();
        assertFalse(result.completed());
        assertTrue(result.timedOut());
        assertEquals(List.of("slow"), result.participantOrder());
    }

}
