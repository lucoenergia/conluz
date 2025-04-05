package org.lucoenergia.conluz.infrastructure.admin.supply;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "supplies_betas")
public class SupplyBetaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "partition_coefficient")
    private double partitionCoefficient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplies_id")
    private SupplyEntity supply;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public double getPartitionCoefficient() {
        return partitionCoefficient;
    }

    public void setPartitionCoefficient(double partitionCoefficient) {
        this.partitionCoefficient = partitionCoefficient;
    }

    public SupplyEntity getSupply() {
        return supply;
    }

    public void setSupply(SupplyEntity supply) {
        this.supply = supply;
    }
}