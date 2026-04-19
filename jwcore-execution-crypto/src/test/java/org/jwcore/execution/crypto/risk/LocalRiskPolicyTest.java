package org.jwcore.execution.crypto.risk;

import org.jwcore.execution.common.state.ExecutionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalRiskPolicyTest {
    @Test
    void shouldInvokePolicyWrapper() {
        final LocalRiskPolicy policy = snapshot -> snapshot.brokerConnected() ? ExecutionState.RUN : ExecutionState.SAFE;
        final ExecutionState connected = policy.evaluate(new LocalRiskSnapshot("crypto", ExecutionState.RUN, true));
        final ExecutionState disconnected = policy.evaluate(new LocalRiskSnapshot("crypto", ExecutionState.RUN, false));
        assertEquals(ExecutionState.RUN, connected);
        assertEquals(ExecutionState.SAFE, disconnected);
    }
}
