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

    private String name;
    private String code;
    private Integer pointType;

    public SupplyDistributorEntity() {
    }

    public static class Builder {
        private UUID supplyId;
        private SupplyEntity supply;
        private String name;
        private String code;
        private Integer pointType;

        public Builder withSupplyId(UUID supplyId) {
            this.supplyId = supplyId;
            return this;
        }

        public Builder withSupply(SupplyEntity supply) {
            this.supply = supply;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withCode(String code) {
            this.code = code;
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
            entity.name = this.name;
            entity.code = this.code;
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

    public String getName() {
        return name;
    }

    public void setName(String distributor) {
        this.name = distributor;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String distributorCode) {
        this.code = distributorCode;
    }

    public Integer getPointType() {
        return pointType;
    }

    public void setPointType(Integer pointType) {
        this.pointType = pointType;
    }
}
