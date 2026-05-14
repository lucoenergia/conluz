package org.lucoenergia.conluz.infrastructure.admin.supply.distributor;

import jakarta.persistence.*;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;

import java.util.UUID;

@Entity(name = "supplies_distributor")
public class SupplyDistributorEntity {

    @Id
    private UUID supplyId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "supply_id")
    private SupplyEntity supply;

    private String distributor;
    private String distributorCode;
    private Integer pointType;

    public SupplyDistributorEntity() {
    }

    public static class Builder {
        private UUID supplyId;
        private SupplyEntity supply;
        private String distributor;
        private String distributorCode;
        private Integer pointType;

        public Builder withSupplyId(UUID supplyId) {
            this.supplyId = supplyId;
            return this;
        }

        public Builder withSupply(SupplyEntity supply) {
            this.supply = supply;
            return this;
        }

        public Builder withDistributor(String distributor) {
            this.distributor = distributor;
            return this;
        }

        public Builder withDistributorCode(String distributorCode) {
            this.distributorCode = distributorCode;
            return this;
        }

        public Builder withPointType(Integer pointType) {
            this.pointType = pointType;
            return this;
        }

        public SupplyDistributorEntity build() {
            SupplyDistributorEntity entity = new SupplyDistributorEntity();
            entity.supplyId = this.supplyId;
            entity.supply = this.supply;
            entity.distributor = this.distributor;
            entity.distributorCode = this.distributorCode;
            entity.pointType = this.pointType;
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

    public String getDistributor() {
        return distributor;
    }

    public void setDistributor(String distributor) {
        this.distributor = distributor;
    }

    public String getDistributorCode() {
        return distributorCode;
    }

    public void setDistributorCode(String distributorCode) {
        this.distributorCode = distributorCode;
    }

    public Integer getPointType() {
        return pointType;
    }

    public void setPointType(Integer pointType) {
        this.pointType = pointType;
    }
}
