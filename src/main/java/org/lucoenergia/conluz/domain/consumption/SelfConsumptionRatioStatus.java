package org.lucoenergia.conluz.domain.consumption;

/**
 * Whether a self-consumption ratio could be computed, and why not when it could not.
 */
public enum SelfConsumptionRatioStatus {

    /**
     * The ratio is computed.
     */
    AVAILABLE,

    /**
     * No energy was assigned to the supplies the ratio covers, so there is nothing to divide by. This is
     * also the status of a period without any data.
     */
    NOTHING_ASSIGNED,

    /**
     * Every supply in scope is a plant's own supply, which the ratio never covers.
     */
    NOT_APPLICABLE
}
