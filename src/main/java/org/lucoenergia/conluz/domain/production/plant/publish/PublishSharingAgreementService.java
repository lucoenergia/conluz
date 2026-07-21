package org.lucoenergia.conluz.domain.production.plant.publish;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;

import java.util.UUID;

public interface PublishSharingAgreementService {

    /**
     * Transitions a sharing agreement from DRAFT to PUBLISHED.
     *
     * @throws org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotDraftException
     *         if the agreement is not DRAFT
     * @throws org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementHasNoCoefficientsException
     *         if the agreement has no partition coefficients yet
     */
    SharingAgreement publish(UUID plantId, UUID sharingAgreementId);
}
