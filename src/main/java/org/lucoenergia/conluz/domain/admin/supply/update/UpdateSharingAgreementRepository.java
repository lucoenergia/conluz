package org.lucoenergia.conluz.domain.admin.supply.update;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;

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
     * @throws org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException if the sharing agreement is not found
     */
    SharingAgreement update(UUID id, LocalDate startDate, LocalDate endDate);
}