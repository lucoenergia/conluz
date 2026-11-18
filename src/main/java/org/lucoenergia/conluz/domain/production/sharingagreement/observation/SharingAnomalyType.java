package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

/**
 * The kinds of sustained disagreement between what the distributor applies and the registered agreements
 * (OBS-004).
 */
public enum SharingAnomalyType {
    /** No candidate matches, the supply receives energy, and it is in at least one agreement. */
    UNKNOWN_COEFFICIENT,
    /** No candidate matches, the supply receives energy, and it is in no agreement. */
    ENERGY_OUTSIDE_AGREEMENT,
    /** No candidate matches and the supply receives no energy, although every agreement gives it some. */
    NO_ENERGY_IN_AGREEMENT,
    /** A coefficient is applied again after a later one had replaced it. */
    REVERTED,
    /** A coefficient, or a leaving, was applied before the agreement's distributor file was sent. */
    APPLIED_BEFORE_SENT
}
