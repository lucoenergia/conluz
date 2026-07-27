package org.lucoenergia.conluz.domain.production.sharingagreement.delete;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotDraftException;

import java.util.UUID;

public interface DeleteSharingAgreementService {

    /**
     * @throws SharingAgreementNotDraftException
     *         if the agreement is not DRAFT -- deleting a PUBLISHED agreement would destroy the
     *         historical basis of past billing.
     */
    void delete(UUID plantId, UUID sharingAgreementId);
}
