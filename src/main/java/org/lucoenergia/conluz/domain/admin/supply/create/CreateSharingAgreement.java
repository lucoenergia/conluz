package org.lucoenergia.conluz.domain.admin.supply.create;

import java.time.LocalDate;

public class CreateSharingAgreement {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String notes;

    public CreateSharingAgreement(LocalDate startDate, LocalDate endDate, String notes) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.notes = notes;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getNotes() {
        return notes;
    }
}
