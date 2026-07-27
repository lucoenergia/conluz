package org.lucoenergia.conluz.domain.production.sharingagreement.update;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotDraftException;

import java.util.UUID;

public interface UpdateSharingAgreementService {

    /**
     * Replaces the descriptive fields of a DRAFT sharing agreement. Never touches {@code status},
     * {@code plantId}, {@code createdAt} or {@code createdBy}.
     *
     * @throws SharingAgreementNotDraftException
     *         if the agreement is not DRAFT
     */
    SharingAgreement update(UUID plantId, UUID sharingAgreementId, UpdateSharingAgreement update);
}
