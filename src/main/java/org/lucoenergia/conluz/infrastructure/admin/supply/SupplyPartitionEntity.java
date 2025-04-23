package org.lucoenergia.conluz.infrastructure.admin.supply;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "supplies_partitions")
public class SupplyPartitionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "coefficient")
    private double coefficient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supply_id")
    private SupplyEntity supply;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sharing_agreement_id")
    private SharingAgreementEntity sharingAgreement;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public double getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(double partitionCoefficient) {
        this.coefficient = partitionCoefficient;
    }

    public SupplyEntity getSupply() {
        return supply;
    }

    public void setSupply(SupplyEntity supply) {
        this.supply = supply;
    }

    public SharingAgreementEntity getSharingAgreement() {
        return sharingAgreement;
    }

    public void setSharingAgreement(SharingAgreementEntity sharingAgreement) {
        this.sharingAgreement = sharingAgreement;
    }
}