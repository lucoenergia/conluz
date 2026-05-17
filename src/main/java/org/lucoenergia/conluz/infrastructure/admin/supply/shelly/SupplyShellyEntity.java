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

    private String macAddress;
    private String id;
    private String mqttPrefix;

    public SupplyShellyEntity() {
    }

    public static class Builder {
        private UUID supplyId;
        private SupplyEntity supply;
        private String macAddress;
        private String id;
        private String mqttPrefix;

        public Builder withSupplyId(UUID supplyId) {
            this.supplyId = supplyId;
            return this;
        }

        public Builder withSupply(SupplyEntity supply) {
            this.supply = supply;
            return this;
        }

        public Builder withMacAddress(String macAddress) {
            this.macAddress = macAddress;
            return this;
        }

        public Builder withId(String shellyId) {
            this.id = shellyId;
            return this;
        }

        public Builder withMqttPrefix(String mqttPrefix) {
            this.mqttPrefix = mqttPrefix;
            return this;
        }

        public SupplyShellyEntity build() {
            SupplyShellyEntity entity = new SupplyShellyEntity();
            entity.supplyId = this.supplyId;
            entity.supply = this.supply;
            entity.macAddress = this.macAddress;
            entity.id = this.id;
            entity.mqttPrefix = this.mqttPrefix;
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

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String shellyMac) {
        this.macAddress = shellyMac;
    }

    public String getId() {
        return id;
    }

    public void setId(String shellyId) {
        this.id = shellyId;
    }

    public String getMqttPrefix() {
        return mqttPrefix;
    }

    public void setMqttPrefix(String shellyMqttPrefix) {
        this.mqttPrefix = shellyMqttPrefix;
    }
}
