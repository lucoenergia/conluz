package org.lucoenergia.conluz.domain.production.sharingagreement;

import java.util.UUID;

/**
 * Raised when the same supplyId appears more than once in one supplyId-keyed coefficient-
 * materialisation call (the manual PUT path). The supplyId-keyed sibling of
 * {@link DuplicatePartitionCoefficientEntryException}, which stays CUPS-keyed for the
 * distributor-file upload path.
 */
public class DuplicatePartitionCoefficientSupplyException extends RuntimeException {

    private final UUID sharingAgreementId;
    private final UUID supplyId;

    public DuplicatePartitionCoefficientSupplyException(UUID sharingAgreementId, UUID supplyId) {
        this.sharingAgreementId = sharingAgreementId;
        this.supplyId = supplyId;
    }

    public UUID getSharingAgreementId() {
        return sharingAgreementId;
    }

    public UUID getSupplyId() {
        return supplyId;
    }
}
