package org.lucoenergia.conluz.domain.admin.supply.sharingagreement;

/**
 * Service for retrieving sharing agreements
 */
public interface GetSharingAgreementService {

    /**
     * Gets a sharing agreement by its ID
     *
     * @param id the ID of the sharing agreement to retrieve
     * @return the sharing agreement
     * @throws SharingAgreementNotFoundException if the sharing agreement is not found
     */
    SharingAgreement getById(SharingAgreementId id);
}