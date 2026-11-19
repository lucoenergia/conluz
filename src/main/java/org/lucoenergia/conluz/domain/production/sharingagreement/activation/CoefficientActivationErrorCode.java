package org.lucoenergia.conluz.domain.production.sharingagreement.activation;

/**
 * One value per per-item validation rule of the {@code setValidFrom}/{@code setValidTo} batch
 * operations (activate/deactivate/close/reopen). {@code SharingAgreementNotPublishedException} is a
 * separate, agreement-level precondition -- not one of these -- since it fails the whole batch before
 * any item is evaluated, rather than accumulating per coefficient.
 */
public enum CoefficientActivationErrorCode {
    /** The id does not belong to the target agreement (unknown, or belongs to another agreement). Params: coefficientId, cups. */
    COEFFICIENT_NOT_IN_AGREEMENT,
    /** appliedOn/closedOn is after today, evaluated in the resolved zone. Params: coefficientId, cups, date. */
    DATE_IN_FUTURE,
    /** correct: newValidFrom is on or after the close an author set on the coefficient itself, which a correction
     * keeps (SHA-004). A validTo the cascade wrote is recomputed from the new neighbours instead, so it never raises
     * this. Params: coefficientId, cups. */
    ACTIVATION_DATE_NOT_BEFORE_SUCCESSOR,
    /** close/reopen: a successor coefficient already starts exactly at the current validTo -- that
     * boundary was written by the cascade, not authored, so it cannot be corrected or reopened here. Params: coefficientId, cups. */
    COEFFICIENT_HAS_SUCCESSOR,
    /** close: the coefficient has never been activated (validFrom IS NULL) -- there is no start for a
     * close date to be "after". Params: coefficientId, cups. */
    COEFFICIENT_NOT_ACTIVE,
    /** close/correct-close: newValidTo <= coefficient's own validFrom. Distinct from the predecessor/
     * successor codes above, which name a different row. Params: coefficientId, cups. */
    CLOSURE_DATE_NOT_AFTER_ACTIVATION,
    /** The write this item resolves to would overlap another coefficient for the same (plantId,
     * supplyId): either activate/correct asked for a validFrom another coefficient already starts at
     * (SHA-005), or, once every write in this batch is projected onto the supply's full history, the
     * result overlaps a row the checks above did not compare against. Params: coefficientId, cups,
     * conflictingCoefficientId. */
    PERIOD_OVERLAP,
    /** set dates: a supply listed among the leaving closures holds a coefficient, pending or not, in the agreement,
     * so it is not leaving it (SHA-013). Params: supplyId, cups. */
    LEAVING_SUPPLY_IN_AGREEMENT,
    /** set dates: a supply listed among the leaving closures has no activated coefficient starting before the
     * agreement's earliest validFrom, or the agreement has none, so there is no row to close (SHA-013). Params:
     * supplyId, cups. */
    NO_COEFFICIENT_TO_CLOSE
}
