package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

/**
 * How the distributor's application of a coefficient, or of a supply leaving the distribution, compares
 * with what is stored (OBS-002).
 */
public enum ObservationStatus {
    /** Observed, and the stored date is the observed day, within its range, or on or before an open date. */
    APPLIED_SAME_DATE,
    /** Observed, but the stored date differs or is missing. */
    APPLIED_DIFFERENT_DATE,
    /** Not observed, and the agreement records the day its distributor file was sent. */
    PENDING_APPLICATION,
    /** Its value equals the supply's value in the agreement observed before it, so its change is not observable. */
    NOT_DISTINGUISHABLE,
    /** Not observed. */
    NOT_OBSERVED,
    /** The supply has no valid day. */
    NO_DATA,
    /** A row of the plant's own supply, whose share cannot be measured from Datadis (OBS-011). */
    NOT_OBSERVABLE
}
