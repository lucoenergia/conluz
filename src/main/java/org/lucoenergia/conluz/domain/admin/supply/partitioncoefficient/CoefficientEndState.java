package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

/**
 * How/why a coefficient's coverage ends.
 */
public enum CoefficientEndState {

    /**
     * No {@code validTo}, and no later non-DRAFT agreement exists for the plant at all: this is the
     * current/last agreement for this supply.
     */
    OPEN,

    /**
     * No {@code validTo}, no next coefficient for this supply in a later agreement, but a later
     * non-DRAFT agreement of the plant exists: the supply appears to have left without an authored
     * close -- the end must be authored by hand.
     */
    OPEN_ORPHAN,

    /**
     * No {@code validTo}; the same supply has a next coefficient in a later agreement, but it has not
     * been applied yet ({@code validFrom} still null).
     */
    PENDING_SUCCESSION,

    /**
     * The end coincides with an already-applied successor coefficient for the same supply -- either
     * because no {@code validTo} is stored and the next coefficient is already activated, or because
     * the stored {@code validTo} was written automatically by the activation cascade rather than
     * authored explicitly.
     */
    DERIVED,

    /**
     * {@code validTo} is stored and was authored explicitly (a close/baja), not written by the
     * activation cascade. Reopenable via the reopen endpoint.
     */
    CLOSED
}
