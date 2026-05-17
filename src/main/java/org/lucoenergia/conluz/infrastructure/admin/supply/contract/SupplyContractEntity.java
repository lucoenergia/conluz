package org.lucoenergia.conluz.infrastructure.admin.supply.contract;

import jakarta.persistence.*;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;

import java.time.LocalDate;
import java.util.UUID;

@Entity(name = "supplies_contract")
public class SupplyContractEntity {

    @Id
    private UUID supplyId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "supply_id")
    private SupplyEntity supply;

    private LocalDate validDateFrom;

    public SupplyContractEntity() {
    }

    public static class Builder {
        private UUID supplyId;
        private SupplyEntity supply;
        private LocalDate validDateFrom;

        public Builder withSupplyId(UUID supplyId) {
            this.supplyId = supplyId;
            return this;
        }

        public Builder withSupply(SupplyEntity supply) {
            this.supply = supply;
            return this;
        }

        public Builder withValidDateFrom(LocalDate validDateFrom) {
            this.validDateFrom = validDateFrom;
            return this;
        }

        public SupplyContractEntity build() {
            SupplyContractEntity entity = new SupplyContractEntity();
            entity.supplyId = this.supplyId;
            entity.supply = this.supply;
            entity.validDateFrom = this.validDateFrom;
            return entity;
        }
    }

    public UUID getSupplyId() {
        return supplyId;
    }

    public void setSupplyId(UUID supplyId) {
        this.supplyId = supplyId;
    }

    public SupplyEntity getSupply() {
        return supply;
    }

    public void setSupply(SupplyEntity supply) {
        this.supply = supply;
    }

    public LocalDate getValidDateFrom() {
        return validDateFrom;
    }

    public void setValidDateFrom(LocalDate validDateFrom) {
        this.validDateFrom = validDateFrom;
    }
}
