package org.lucoenergia.conluz.domain.admin.supply;

import java.util.UUID;

public class SupplyPartition {

    private final UUID id;
    private final Supply supply;
    private final SharingAgreement agreement;
    private final Double coefficient;

    public SupplyPartition(UUID id, Supply supply, SharingAgreement agreement, Double coefficient) {
        this.id = id;
        this.supply = supply;
        this.agreement = agreement;
        this.coefficient = coefficient;
    }

    public UUID getId() {
        return id;
    }

    public Supply getSupply() {
        return supply;
    }

    public SharingAgreement getAgreement() {
        return agreement;
    }

    public Double getCoefficient() {
        return coefficient;
    }
}
