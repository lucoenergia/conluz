package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for sharing agreements
 */
public class SharingAgreementResponse {

    private final UUID id;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public SharingAgreementResponse(SharingAgreement sharingAgreement) {
        this.id = sharingAgreement.getId();
        this.startDate = sharingAgreement.getStartDate();
        this.endDate = sharingAgreement.getEndDate();
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}