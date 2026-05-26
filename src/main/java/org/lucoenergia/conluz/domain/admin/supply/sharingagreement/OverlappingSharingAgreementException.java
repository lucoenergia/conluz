package org.lucoenergia.conluz.domain.admin.supply.sharingagreement;

import java.time.LocalDate;
import java.util.UUID;

public class OverlappingSharingAgreementException extends RuntimeException {

    public OverlappingSharingAgreementException(LocalDate startDate, LocalDate endDate, UUID existing) {
        super(String.format("New sharing agreement with date range [%s, %s] overlaps with an existing agreement with ID %s",
                startDate, endDate, existing));
    }
}
