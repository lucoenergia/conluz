package org.lucoenergia.conluz.domain.admin.supply.delete;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;

/**
 * Repository for deleting sharing agreements
 */
public interface DeleteSharingAgreementRepository {

    /**
     * Deletes a sharing agreement by its ID
     *
     * @param id the ID of the sharing agreement to delete
     * @throws org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException if the sharing agreement is not found
     */
    void delete(SharingAgreementId id);
}