package org.lucoenergia.conluz.domain.production.plant.update;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;

import java.util.UUID;

public interface UpdateSharingAgreementService {

    /**
     * Replaces the descriptive fields of a DRAFT sharing agreement. Never touches {@code status},
     * {@code plantId}, {@code createdAt} or {@code createdBy}.
     *
     * @throws org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotDraftException
     *         if the agreement is not DRAFT
     */
    SharingAgreement update(UUID plantId, UUID sharingAgreementId, UpdateSharingAgreement update);
}
