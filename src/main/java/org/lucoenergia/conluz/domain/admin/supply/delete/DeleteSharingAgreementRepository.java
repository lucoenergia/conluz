package org.lucoenergia.conluz.domain.admin.supply.delete;

import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreementNotFoundException;

/**
 * Repository for deleting sharing agreements
 */
public interface DeleteSharingAgreementRepository {

    /**
     * Deletes a sharing agreement by its ID
     *
     * @param id the ID of the sharing agreement to delete
     * @throws SharingAgreementNotFoundException if the sharing agreement is not found
     */
    void delete(SharingAgreementId id);
}