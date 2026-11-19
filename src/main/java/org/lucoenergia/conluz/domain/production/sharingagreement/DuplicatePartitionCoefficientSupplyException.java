package org.lucoenergia.conluz.domain.production.sharingagreement;

import java.util.UUID;

/**
 * Raised when a supply would hold more than one coefficient in one sharing agreement (SHA-018):
 * when the same supplyId appears more than once in one supplyId-keyed coefficient-materialisation
 * call, or when a write that bypasses that check is rejected by the database constraint on
 * (sharing_agreement_id, supply_id). The supplyId-keyed sibling of
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

    public DuplicatePartitionCoefficientSupplyException(UUID sharingAgreementId, UUID supplyId, Throwable cause) {
        super(cause);
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
