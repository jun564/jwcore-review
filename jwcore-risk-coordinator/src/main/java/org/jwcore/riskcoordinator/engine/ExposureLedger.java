package org.jwcore.riskcoordinator.engine;

import org.jwcore.domain.CanonicalId;
import org.jwcore.domain.EventEnvelope;
import org.jwcore.domain.EventType;
import org.jwcore.domain.OrderSide;
import org.jwcore.domain.events.OrderCanceledEvent;
import org.jwcore.domain.events.OrderFilledEvent;
import org.jwcore.domain.events.OrderRejectedEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ExposureLedger {
    static final int SCALE = 8;
    static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    static final BigDecimal MARGIN_RATE = new BigDecimal("0.01");

    private final Map<CanonicalId, PositionState> positions = new HashMap<>();
    private final Map<CanonicalId, Integer> intentCounts = new HashMap<>();

    public void apply(final EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        if (envelope.eventType() == EventType.OrderIntentEvent) {
            requireCanonicalId(envelope.canonicalId());
            incrementIntent(envelope.canonicalId());
            return;
        }

        if (envelope.eventType() == EventType.OrderRejectedEvent) {
            final OrderRejectedEvent event = OrderRejectedEvent.fromPayload(envelope.payload());
            final CanonicalId canonicalId = requireCanonicalId(envelope.canonicalId());
            decrementIntentOrFail(canonicalId, "Reject without pending intent for canonicalId=%s");
            return;
        }

        if (envelope.eventType() == EventType.OrderCanceledEvent) {
            final OrderCanceledEvent event = OrderCanceledEvent.fromPayload(envelope.payload());
            decrementIntentOrFail(event.canonicalId(), "Cancel without pending intent for canonicalId=%s");
            return;
        }

        if (envelope.eventType() == EventType.OrderFilledEvent) {
            final OrderFilledEvent event = OrderFilledEvent.fromPayload(envelope.payload());
            decrementIntentOrFail(event.canonicalId(), "Fill without pending intent for canonicalId=%s");
            applyFill(event);
        }
    }

    public BigDecimal netPosition(final CanonicalId canonicalId) {
        return stateOf(canonicalId).netPosition();
    }

    public BigDecimal averageEntryPrice(final CanonicalId canonicalId) {
        return stateOf(canonicalId).averageEntryPrice();
    }

    public BigDecimal realizedPnL(final CanonicalId canonicalId) {
        return stateOf(canonicalId).realizedPnL();
    }

    public BigDecimal totalCommission(final CanonicalId canonicalId) {
        return stateOf(canonicalId).totalCommission();
    }

    public int intentCount(final CanonicalId canonicalId) {
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        return intentCounts.getOrDefault(canonicalId, 0);
    }

    public BigDecimal totalExposure() {
        BigDecimal total = BigDecimal.ZERO;
        for (final PositionState state : positions.values()) {
            total = total.add(state.netPosition().abs().multiply(state.averageEntryPrice()));
        }
        return total;
    }

    public BigDecimal marginUsed() {
        return totalExposure().multiply(MARGIN_RATE);
    }

    private void applyFill(final OrderFilledEvent event) {
        final PositionState current = positions.getOrDefault(event.canonicalId(), PositionState.empty());

        BigDecimal netPosition = current.netPosition();
        BigDecimal averageEntryPrice = current.averageEntryPrice();
        BigDecimal realizedPnL = current.realizedPnL().subtract(event.commission());
        BigDecimal totalCommission = current.totalCommission().add(event.commission());

        if (netPosition.signum() == 0) {
            netPosition = signedQuantity(event.side(), event.filledQuantity());
            averageEntryPrice = event.averagePrice();
            positions.put(event.canonicalId(), new PositionState(netPosition, averageEntryPrice, realizedPnL, totalCommission));
            return;
        }

        final boolean sameDirection = (netPosition.signum() > 0 && event.side() == OrderSide.BUY)
                || (netPosition.signum() < 0 && event.side() == OrderSide.SELL);

        if (sameDirection) {
            final BigDecimal existingAbs = netPosition.abs();
            final BigDecimal denominator = existingAbs.add(event.filledQuantity());
            final BigDecimal weighted = existingAbs.multiply(averageEntryPrice)
                    .add(event.filledQuantity().multiply(event.averagePrice()))
                    .divide(denominator, SCALE, ROUNDING_MODE);

            netPosition = netPosition.add(signedQuantity(event.side(), event.filledQuantity()));
            averageEntryPrice = weighted;
            positions.put(event.canonicalId(), new PositionState(netPosition, averageEntryPrice, realizedPnL, totalCommission));
            return;
        }

        final BigDecimal closedQuantity = event.filledQuantity().min(netPosition.abs());
        final BigDecimal pnlFromClose;
        if (netPosition.signum() > 0) {
            pnlFromClose = event.averagePrice().subtract(averageEntryPrice).multiply(closedQuantity);
        } else {
            pnlFromClose = averageEntryPrice.subtract(event.averagePrice()).multiply(closedQuantity);
        }
        realizedPnL = realizedPnL.add(pnlFromClose);

        if (event.filledQuantity().compareTo(netPosition.abs()) <= 0) {
            netPosition = netPosition.add(signedQuantity(event.side(), event.filledQuantity()));
            if (netPosition.signum() == 0) {
                averageEntryPrice = BigDecimal.ZERO;
            }
        } else {
            final BigDecimal overflow = event.filledQuantity().subtract(netPosition.abs());
            netPosition = signedQuantity(event.side(), overflow);
            averageEntryPrice = event.averagePrice();
        }

        positions.put(event.canonicalId(), new PositionState(netPosition, averageEntryPrice, realizedPnL, totalCommission));
    }

    private void incrementIntent(final CanonicalId canonicalId) {
        intentCounts.merge(canonicalId, 1, Integer::sum);
        // TODO: consider pending intents keyed by orderId when lifecycle model becomes per-order.
    }

    private void decrementIntentOrFail(final CanonicalId canonicalId, final String messageFormat) {
        final int current = intentCounts.getOrDefault(canonicalId, 0);
        if (current == 0) {
            throw new IllegalStateException(messageFormat.formatted(canonicalId.format()));
        }
        if (current == 1) {
            intentCounts.remove(canonicalId);
            return;
        }
        intentCounts.put(canonicalId, current - 1);
    }

    private static CanonicalId requireCanonicalId(final CanonicalId canonicalId) {
        return Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
    }

    private static PositionState stateOf(final CanonicalId canonicalId, final Map<CanonicalId, PositionState> positions) {
        Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        return positions.getOrDefault(canonicalId, PositionState.empty());
    }

    private PositionState stateOf(final CanonicalId canonicalId) {
        return stateOf(canonicalId, positions);
    }

    private static BigDecimal signedQuantity(final OrderSide side, final BigDecimal quantity) {
        return side == OrderSide.BUY ? quantity : quantity.negate();
    }

    private record PositionState(
            BigDecimal netPosition,
            BigDecimal averageEntryPrice,
            BigDecimal realizedPnL,
            BigDecimal totalCommission) {
        private static PositionState empty() {
            return new PositionState(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}
