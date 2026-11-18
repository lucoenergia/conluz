package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

/**
 * Why the observation of a plant could not be computed (OBS-006).
 */
public enum NotComputableReason {
    /** The plant supply has no production record, or no month published under ENM-001. */
    PLANT_SUPPLY_WITHOUT_DATA
}
