package org.lucoenergia.conluz.domain.admin.supply.sharingagreement;

import java.time.LocalDate;

public enum SharingAgreementStatus {
    ACTIVE,
    PREVIOUS;

    public static SharingAgreementStatus from(LocalDate endDate) {
        if (endDate == null || !endDate.isBefore(LocalDate.now())) {
            return ACTIVE;
        }
        return PREVIOUS;
    }
}
