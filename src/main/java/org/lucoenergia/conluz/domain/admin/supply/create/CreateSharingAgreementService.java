package org.lucoenergia.conluz.domain.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for creating sharing agreements
 */
public interface CreateSharingAgreementService {

    /**
     * Creates a new sharing agreement
     *
     * @param startDate the start date of the agreement
     * @param endDate the end date of the agreement
     * @return the created sharing agreement
     */
    SharingAgreement create(LocalDate startDate, LocalDate endDate, UUID communityId);
}