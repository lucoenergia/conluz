package org.lucoenergia.conluz.domain.admin.supply.update;

import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreementNotFoundException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Repository for updating sharing agreements
 */
public interface UpdateSharingAgreementRepository {

    /**
     * Updates a sharing agreement
     *
     * @param id the ID of the sharing agreement to update
     * @param startDate the new start date
     * @param endDate the new end date
     * @return the updated sharing agreement
     * @throws SharingAgreementNotFoundException if the sharing agreement is not found
     */
    SharingAgreement update(UUID id, LocalDate startDate, LocalDate endDate);
}