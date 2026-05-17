package org.lucoenergia.conluz.infrastructure.admin.supply.datadis;

import jakarta.persistence.*;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;

import java.util.UUID;

@Entity(name = "supplies_datadis")
public class SupplyDatadisEntity {

    @Id
    private UUID supplyId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "supply_id")
    private SupplyEntity supply;

    private Boolean thirdParty;

    public SupplyDatadisEntity() {
    }

    public static class Builder {
        private UUID supplyId;
        private SupplyEntity supply;
        private Boolean thirdParty;

        public Builder withSupplyId(UUID supplyId) {
            this.supplyId = supplyId;
            return this;
        }

        public Builder withSupply(SupplyEntity supply) {
            this.supply = supply;
            return this;
        }

        public Builder withThirdParty(Boolean thirdParty) {
            this.thirdParty = thirdParty;
            return this;
        }

        public SupplyDatadisEntity build() {
            SupplyDatadisEntity entity = new SupplyDatadisEntity();
            entity.supplyId = this.supplyId;
            entity.supply = this.supply;
            entity.thirdParty = this.thirdParty;
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

    public Boolean getThirdParty() {
        return thirdParty;
    }

    public void setThirdParty(Boolean thirdParty) {
        this.thirdParty = thirdParty;
    }
}
