package org.lucoenergia.conluz.domain.production.sharingagreement.publish;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementCoefficientSumInvalidException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementHasNoCoefficientsException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotDraftException;

import java.util.UUID;

public interface PublishSharingAgreementService {

    /**
     * Transitions a sharing agreement from DRAFT to PUBLISHED.
     *
     * @throws SharingAgreementNotDraftException
     *         if the agreement is not DRAFT
     * @throws SharingAgreementHasNoCoefficientsException
     *         if the agreement has no partition coefficients yet
     * @throws SharingAgreementCoefficientSumInvalidException
     *         if the agreement's coefficients do not sum to exactly 1
     */
    SharingAgreement publish(UUID plantId, UUID sharingAgreementId);
}
