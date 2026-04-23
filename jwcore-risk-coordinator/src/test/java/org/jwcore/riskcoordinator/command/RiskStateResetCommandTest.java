package org.jwcore.riskcoordinator.command;

import org.jwcore.domain.CanonicalId;
import org.jwcore.execution.common.state.ExecutionState;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class RiskStateResetCommandTest {
    private static final CanonicalId CID = CanonicalId.parse("S07:I03:VA07-03:BA01");
    private static final Instant TS = Instant.parse("2026-04-23T10:00:00Z");
    private static final String REASON = "manual reset after review";

    @Test void nullCanonicalId() { assertThrows(NullPointerException.class, () -> new RiskStateResetCommand(null, ExecutionState.RUN, "op1", REASON, TS)); }
    @Test void nullTargetState() { assertThrows(NullPointerException.class, () -> new RiskStateResetCommand(CID, null, "op1", REASON, TS)); }
    @Test void nullOperatorId() { assertThrows(NullPointerException.class, () -> new RiskStateResetCommand(CID, ExecutionState.RUN, null, REASON, TS)); }
    @Test void nullReason() { assertThrows(NullPointerException.class, () -> new RiskStateResetCommand(CID, ExecutionState.RUN, "op1", null, TS)); }
    @Test void nullRequestedAt() { assertThrows(NullPointerException.class, () -> new RiskStateResetCommand(CID, ExecutionState.RUN, "op1", REASON, null)); }
    @Test void blankOperatorId() { assertThrows(IllegalArgumentException.class, () -> new RiskStateResetCommand(CID, ExecutionState.RUN, "   ", REASON, TS)); }
    @Test void shortReason() { assertThrows(IllegalArgumentException.class, () -> new RiskStateResetCommand(CID, ExecutionState.RUN, "op1", "short", TS)); }
    @Test void targetSafe() { assertThrows(IllegalArgumentException.class, () -> new RiskStateResetCommand(CID, ExecutionState.SAFE, "op1", REASON, TS)); }
    @Test void targetHalt() { assertThrows(IllegalArgumentException.class, () -> new RiskStateResetCommand(CID, ExecutionState.HALT, "op1", REASON, TS)); }
    @Test void validCommand() { assertDoesNotThrow(() -> new RiskStateResetCommand(CID, ExecutionState.RUN, "op1", REASON, TS)); }
}
