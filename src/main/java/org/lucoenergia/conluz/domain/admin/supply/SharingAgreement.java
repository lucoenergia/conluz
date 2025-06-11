package org.lucoenergia.conluz.domain.admin.supply;

import java.time.LocalDate;
import java.util.UUID;

public class SharingAgreement {

    private final UUID id;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public SharingAgreement(UUID id, LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
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
