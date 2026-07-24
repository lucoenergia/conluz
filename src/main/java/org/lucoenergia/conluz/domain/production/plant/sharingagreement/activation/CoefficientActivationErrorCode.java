package org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation;

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
    /** activate/correct: newValidFrom <= predecessor.validFrom (covers the empty-range case). Params: coefficientId, cups. */
    ACTIVATION_DATE_NOT_AFTER_PREDECESSOR,
    /** activate/correct: newValidFrom >= coefficient's own validTo. Params: coefficientId, cups. */
    ACTIVATION_DATE_NOT_BEFORE_SUCCESSOR,
    /** close/reopen: a successor coefficient already starts exactly at the current validTo -- that
     * boundary was written by the cascade, not authored, so it cannot be corrected or reopened here. Params: coefficientId, cups. */
    COEFFICIENT_HAS_SUCCESSOR,
    /** close: the coefficient has never been activated (validFrom IS NULL) -- there is no start for a
     * close date to be "after". Params: coefficientId, cups. */
    COEFFICIENT_NOT_ACTIVE,
    /** close/correct-close: newValidTo <= coefficient's own validFrom. Distinct from the predecessor/
     * successor codes above, which name a different row. Params: coefficientId, cups. */
    CLOSURE_DATE_NOT_AFTER_ACTIVATION
}
