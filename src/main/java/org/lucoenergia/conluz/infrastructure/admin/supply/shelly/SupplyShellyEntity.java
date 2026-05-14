package org.lucoenergia.conluz.infrastructure.admin.supply.shelly;

import jakarta.persistence.*;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;

import java.util.UUID;

@Entity(name = "supplies_shelly")
public class SupplyShellyEntity {

    @Id
    private UUID supplyId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "supply_id")
    private SupplyEntity supply;

    private String shellyMac;
    private String shellyId;
    private String shellyMqttPrefix;

    public SupplyShellyEntity() {
    }

    public static class Builder {
        private UUID supplyId;
        private SupplyEntity supply;
        private String shellyMac;
        private String shellyId;
        private String shellyMqttPrefix;

        public Builder withSupplyId(UUID supplyId) {
            this.supplyId = supplyId;
            return this;
        }

        public Builder withSupply(SupplyEntity supply) {
            this.supply = supply;
            return this;
        }

        public Builder withShellyMac(String shellyMac) {
            this.shellyMac = shellyMac;
            return this;
        }

        public Builder withShellyId(String shellyId) {
            this.shellyId = shellyId;
            return this;
        }

        public Builder withShellyMqttPrefix(String shellyMqttPrefix) {
            this.shellyMqttPrefix = shellyMqttPrefix;
            return this;
        }

        public SupplyShellyEntity build() {
            SupplyShellyEntity entity = new SupplyShellyEntity();
            entity.supplyId = this.supplyId;
            entity.supply = this.supply;
            entity.shellyMac = this.shellyMac;
            entity.shellyId = this.shellyId;
            entity.shellyMqttPrefix = this.shellyMqttPrefix;
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

    public String getShellyMac() {
        return shellyMac;
    }

    public void setShellyMac(String shellyMac) {
        this.shellyMac = shellyMac;
    }

    public String getShellyId() {
        return shellyId;
    }

    public void setShellyId(String shellyId) {
        this.shellyId = shellyId;
    }

    public String getShellyMqttPrefix() {
        return shellyMqttPrefix;
    }

    public void setShellyMqttPrefix(String shellyMqttPrefix) {
        this.shellyMqttPrefix = shellyMqttPrefix;
    }
}
