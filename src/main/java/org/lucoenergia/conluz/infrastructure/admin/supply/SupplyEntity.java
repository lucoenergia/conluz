package org.lucoenergia.conluz.infrastructure.admin.supply;

import jakarta.persistence.*;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name = "supplies")
public class SupplyEntity {

    @Id
    private UUID id;
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserEntity user;

    private String name;
    private String address;
    private Float partitionCoefficient;
    private Boolean enabled;
    private LocalDate validDateFrom;
    private String distributor;
    private String distributorCode;
    private Integer pointType;
    private Boolean thirdParty;
    private String shellyMac;
    private String shellyId;
    private String shellyMqttPrefix;

    @OneToMany(
            mappedBy = "supply",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PlantEntity> plants = new ArrayList<>();

    @OneToMany(mappedBy = "supplies", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<SupplyBetaEntity> betas;

    public SupplyEntity() {
        enabled = true;
    }

    public static class Builder {
        private UUID id;
        private String code;
        private UserEntity user;
        private String name;
        private String address;
        private Float partitionCoefficient;
        private Boolean enabled;

        private LocalDate validDateFrom;
        private String distributor;
        private String distributorCode;
        private Integer pointType;
        private Boolean thirdParty;
        private String shellyMac;
        private String shellyId;
        private String shellyMqttPrefix;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withCode(String code) {
            this.code = code;
            return this;
        }

        public Builder withUser(UserEntity user) {
            this.user = user;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withAddress(String address) {
            this.address = address;
            return this;
        }

        public Builder withPartitionCoefficient(Float partitionCoefficient) {
            this.partitionCoefficient = partitionCoefficient;
            return this;
        }

        public Builder withEnabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder withValidDateFrom(LocalDate validDateFrom) {
            this.validDateFrom = validDateFrom;
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

        public Builder withThirdParty(Boolean thirdParty) {
            this.thirdParty = thirdParty;
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

        public SupplyEntity build() {
            SupplyEntity entity = new SupplyEntity();
            entity.id = this.id;
            entity.code = this.code;
            entity.user = this.user;
            entity.name = this.name;
            entity.address = this.address;
            entity.partitionCoefficient = this.partitionCoefficient;
            entity.enabled = this.enabled;
            entity.validDateFrom = this.validDateFrom;
            entity.distributor = this.distributor;
            entity.distributorCode = this.distributorCode;
            entity.pointType = this.pointType;
            entity.thirdParty = this.thirdParty;
            entity.shellyMac = this.shellyMac;
            entity.shellyId = this.shellyId;
            entity.shellyMqttPrefix = this.shellyMqttPrefix;
            return entity;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplyEntity )) return false;
        return code != null && code.equals(((SupplyEntity) o).getCode());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }


    public void setAddress(String address) {
        this.address = address;
    }

    public Float getPartitionCoefficient() {
        return partitionCoefficient;
    }

    public void setPartitionCoefficient(Float partitionCoefficient) {
        this.partitionCoefficient = partitionCoefficient;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public LocalDate getValidDateFrom() {
        return validDateFrom;
    }

    public void setValidDateFrom(LocalDate validDateFrom) {
        this.validDateFrom = validDateFrom;
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

    public Boolean getThirdParty() {
        return thirdParty;
    }

    public void setThirdParty(Boolean datadisIsThirdParty) {
        this.thirdParty = datadisIsThirdParty;
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

    public List<PlantEntity> getPlants() {
        return plants;
    }

    public void addPlant(PlantEntity plant) {
        plants.add(plant);
        plant.setSupply(this);
    }

    public void removePlant(PlantEntity plant) {
        plants.remove(plant);
        plant.setSupply(null);
    }

    public List<SupplyBetaEntity> getBetas() {
        return betas;
    }

    public void addBeta(SupplyBetaEntity beta) {
        betas.add(beta);
        beta.setSupply(this);
    }

    public void removeBeta(SupplyBetaEntity beta) {
        betas.remove(beta);
        beta.setSupply(null);
    }
}
