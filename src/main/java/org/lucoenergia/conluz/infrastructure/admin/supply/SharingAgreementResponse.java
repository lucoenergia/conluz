package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class SharingAgreementResponse {

    private final UUID id;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String notes;
    private final SharingAgreementStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final int supplyCount;
    private final BigDecimal coefficientSum;

    public SharingAgreementResponse(SharingAgreement sharingAgreement) {
        this.id = sharingAgreement.getId();
        this.startDate = sharingAgreement.getStartDate();
        this.endDate = sharingAgreement.getEndDate();
        this.notes = sharingAgreement.getNotes();
        this.status = SharingAgreementStatus.from(sharingAgreement.getEndDate());
        this.createdAt = sharingAgreement.getCreatedAt();
        this.updatedAt = sharingAgreement.getUpdatedAt();
        this.supplyCount = 0;
        this.coefficientSum = null;
    }

    public SharingAgreementResponse(SharingAgreement sharingAgreement, List<SupplyPartitionEntity> partitions) {
        this.id = sharingAgreement.getId();
        this.startDate = sharingAgreement.getStartDate();
        this.endDate = sharingAgreement.getEndDate();
        this.notes = sharingAgreement.getNotes();
        this.status = SharingAgreementStatus.from(sharingAgreement.getEndDate());
        this.createdAt = sharingAgreement.getCreatedAt();
        this.updatedAt = sharingAgreement.getUpdatedAt();
        this.supplyCount = partitions.size();
        this.coefficientSum = partitions.stream()
                .map(p -> BigDecimal.valueOf(p.getCoefficient()).multiply(BigDecimal.valueOf(100)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    public String getNotes() {
        return notes;
    }

    public SharingAgreementStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getSupplyCount() {
        return supplyCount;
    }

    public BigDecimal getCoefficientSum() {
        return coefficientSum;
    }
}
