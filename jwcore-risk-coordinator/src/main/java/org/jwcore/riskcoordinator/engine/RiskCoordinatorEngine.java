package org.jwcore.riskcoordinator.engine;

import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.execution.common.state.ExecutionState;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public final class RiskCoordinatorEngine {
    private static final Logger LOGGER = Logger.getLogger(RiskCoordinatorEngine.class.getName());

    private final BigDecimal safeThreshold;
    private final BigDecimal haltThreshold;
    private final Map<String, BigDecimal> latestExposureByAccount = new LinkedHashMap<>();

    public RiskCoordinatorEngine(final BigDecimal safeThreshold, final BigDecimal haltThreshold) {
        this.safeThreshold = Objects.requireNonNull(safeThreshold, "safeThreshold cannot be null");
        this.haltThreshold = Objects.requireNonNull(haltThreshold, "haltThreshold cannot be null");
        if (haltThreshold.compareTo(safeThreshold) < 0) {
            throw new IllegalArgumentException("haltThreshold must be >= safeThreshold");
        }
    }

    public Map<String, ExecutionState> evaluate(final List<EventEnvelope> received) {
        Objects.requireNonNull(received, "received cannot be null");

        final Map<String, BigDecimal> exposureByAccount = new LinkedHashMap<>();
        for (final EventEnvelope envelope : received) {
            if (envelope.eventType() != EventType.OrderIntentEvent) {
                continue;
            }
            final ParsedExposure parsedExposure = parseExposure(envelope);
            if (parsedExposure == null) {
                continue;
            }
            exposureByAccount.merge(parsedExposure.accountId(), parsedExposure.exposure(), BigDecimal::add);
        }

        latestExposureByAccount.clear();
        latestExposureByAccount.putAll(exposureByAccount);

        final Map<String, ExecutionState> decisions = new LinkedHashMap<>();
        for (final Map.Entry<String, BigDecimal> entry : exposureByAccount.entrySet()) {
            decisions.put(entry.getKey(), decisionFor(entry.getValue()));
        }
        return decisions;
    }

    public RiskAssessment evaluate(final BigDecimal cryptoExposure, final BigDecimal forexExposure) {
        Objects.requireNonNull(cryptoExposure, "cryptoExposure cannot be null");
        Objects.requireNonNull(forexExposure, "forexExposure cannot be null");
        final BigDecimal total = cryptoExposure.add(forexExposure);
        return new RiskAssessment(decisionFor(total), total, "legacy evaluation path");
    }

    public Map<String, BigDecimal> latestExposureByAccount() {
        return Map.copyOf(latestExposureByAccount);
    }

    private ParsedExposure parseExposure(final EventEnvelope envelope) {
        final String payloadText = new String(envelope.payload(), StandardCharsets.UTF_8);
        final String[] parts = payloadText.split("\\|", 3);
        if (parts.length != 3 || parts[0].isBlank()) {
            LOGGER.warning(() -> "Skipping malformed OrderIntentEvent for exposure aggregation, eventId=" + envelope.eventId());
            return null;
        }
        try {
            return new ParsedExposure(parts[0], new BigDecimal(parts[2].trim()).abs());
        } catch (final NumberFormatException exception) {
            LOGGER.warning(() -> "Skipping OrderIntentEvent with unparsable exposure, eventId=" + envelope.eventId());
            return null;
        }
    }

    private ExecutionState decisionFor(final BigDecimal exposure) {
        if (exposure.compareTo(haltThreshold) >= 0) {
            return ExecutionState.HALT;
        }
        if (exposure.compareTo(safeThreshold) >= 0) {
            return ExecutionState.SAFE;
        }
        return ExecutionState.RUN;
    }

    private record ParsedExposure(String accountId, BigDecimal exposure) { }
}
