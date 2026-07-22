package org.lucoenergia.conluz.infrastructure.admin.supply;

import jakarta.persistence.*;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supply_partition_coefficient")
public class SupplyPartitionCoefficientEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supply_id", nullable = false)
    private SupplyEntity supply;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id", nullable = false)
    private PlantEntity plant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sharing_agreement_id", nullable = false)
    private SharingAgreementEntity sharingAgreement;

    @Column(name = "coefficient", nullable = false, precision = 18, scale = 6)
    private BigDecimal coefficient;

    /**
     * Nullable since phase 5c: a null valid_from is a "pending" row, materialised (by a distributor
     * file upload or manual authoring) but not yet activated. Activation is a future phase's job.
     */
    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public SupplyEntity getSupply() {
        return supply;
    }

    public void setSupply(SupplyEntity supply) {
        this.supply = supply;
    }

    public PlantEntity getPlant() {
        return plant;
    }

    public void setPlant(PlantEntity plant) {
        this.plant = plant;
    }

    public SharingAgreementEntity getSharingAgreement() {
        return sharingAgreement;
    }

    public void setSharingAgreement(SharingAgreementEntity sharingAgreement) {
        this.sharingAgreement = sharingAgreement;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(BigDecimal coefficient) {
        this.coefficient = coefficient;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public void setValidTo(Instant validTo) {
        this.validTo = validTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
