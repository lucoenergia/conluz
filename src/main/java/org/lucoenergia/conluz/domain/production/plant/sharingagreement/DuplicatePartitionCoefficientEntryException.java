package org.lucoenergia.conluz.domain.production.plant.sharingagreement;

import java.util.UUID;

/**
 * Raised when the same CUPS appears more than once in one coefficient-materialisation call
 * (distributor file upload or manual PUT). The no_overlapping_coefficients exclusion constraint
 * does not catch this for pending rows (its {@code WHERE (valid_from IS NOT NULL)} predicate
 * excludes them), so it is enforced here instead.
 */
public class DuplicatePartitionCoefficientEntryException extends RuntimeException {

    private final UUID sharingAgreementId;
    private final String cups;

    public DuplicatePartitionCoefficientEntryException(UUID sharingAgreementId, String cups) {
        this.sharingAgreementId = sharingAgreementId;
        this.cups = cups;
    }

    public UUID getSharingAgreementId() {
        return sharingAgreementId;
    }

    public String getCups() {
        return cups;
    }
}
