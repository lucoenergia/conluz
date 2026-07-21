package org.lucoenergia.conluz.domain.production.plant.delete;

import java.util.UUID;

public interface DeleteSharingAgreementService {

    /**
     * @throws org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotDraftException
     *         if the agreement is not DRAFT -- deleting a PUBLISHED agreement would destroy the
     *         historical basis of past billing.
     */
    void delete(UUID plantId, UUID sharingAgreementId);
}
