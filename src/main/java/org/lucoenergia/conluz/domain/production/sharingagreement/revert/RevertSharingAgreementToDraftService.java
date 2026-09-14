package org.lucoenergia.conluz.domain.production.sharingagreement.revert;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementHasAppliedCoefficientsException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotRevertibleException;

import java.util.UUID;

public interface RevertSharingAgreementToDraftService {

    /**
     * Transitions a sharing agreement from PUBLISHED back to DRAFT.
     *
     * @throws SharingAgreementNotRevertibleException
     *         if the agreement is not PUBLISHED (already DRAFT, or SUPERSEDED)
     * @throws SharingAgreementHasAppliedCoefficientsException
     *         if any of the agreement's coefficients has already been applied by the distributor
     *         (has {@code validFrom} set)
     */
    SharingAgreement revertToDraft(UUID plantId, UUID sharingAgreementId);
}
