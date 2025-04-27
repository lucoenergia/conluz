package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;

/**
 * Service for retrieving sharing agreements
 */
public interface GetSharingAgreementService {

    /**
     * Gets a sharing agreement by its ID
     *
     * @param id the ID of the sharing agreement to retrieve
     * @return the sharing agreement
     * @throws org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException if the sharing agreement is not found
     */
    SharingAgreement getById(SharingAgreementId id);
}