package org.jwcore.domain;

/**
 * Powody odrzucenia operacji/rządania ryzyka.
 * Enum rozszerzalny o kolejne limity i stany ochronne.
 */
public enum RejectReason {
    SAFE_STATE,
    HALT_STATE,
    RISK_LIMIT
}
