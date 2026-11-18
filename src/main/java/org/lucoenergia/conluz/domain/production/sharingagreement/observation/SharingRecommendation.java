package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

/**
 * What an admin should do with an agreement after its observation (OBS-005). Never a coefficient value.
 */
public enum SharingRecommendation {
    /** A coefficient or leaving row is applied on another date than the stored one, or cannot be observed. */
    SET_DATES,
    /** Nothing of the agreement was observed and it was never sent, like the synthetic agreement. */
    DEACTIVATE,
    /** Anomalies are attributed to the agreement. */
    REVIEW_ANOMALIES,
    /** Nothing to do. */
    NONE
}
