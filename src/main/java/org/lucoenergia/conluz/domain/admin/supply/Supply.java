package org.lucoenergia.conluz.domain.admin.supply;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.shared.uuid.ValidUUID;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Supply {

    @NotNull
    @ValidUUID
    private UUID id;
    @NotBlank
    private String code;
    @NotNull
    private User user;
    private String name;
    @NotBlank
    private String address;
    @NotNull
    private Float partitionCoefficient;
    @NotNull
    private Boolean enabled;
    // Data gathered from datadis.es
    private LocalDate datadisValidDateFrom;
    private String datadisDistributor;
    private String datadisDistributorCode;
    private Integer datadisPointType;
    private Boolean datadisIsThirdParty;
    // Shelly settings
    private String shellyMac;
    private String shellyId;
    private String shellyMqttPrefix;

    private Supply(Builder builder) {
        this.id = builder.id;
        this.code = builder.code;
        this.user = builder.user;
        this.name = builder.name;
        this.address = builder.address;
        this.partitionCoefficient = builder.partitionCoefficient;
        this.enabled = builder.enabled;
        this.datadisValidDateFrom = builder.datadisValidDateFrom;
        this.datadisDistributor = builder.datadisDistributor;
        this.datadisDistributorCode = builder.datadisDistributorCode;
        this.datadisPointType = builder.datadisPointType;
        this.datadisIsThirdParty = builder.datadisIsThirdParty;
        this.shellyId = builder.shellyId;
        this.shellyMqttPrefix = builder.shellyMqttPrefix;
        this.shellyMac = builder.shellyMac;
    }

    public void enable() {
        enabled = true;
    }

    public void initializeUuid() {
        id = UUID.randomUUID();
    }

    public static class Builder {
        private UUID id;
        private String code;
        private User user;
        private String name;
        private String address;
        private Float partitionCoefficient;
        private Boolean enabled;
        private LocalDate datadisValidDateFrom;
        private String datadisDistributor;
        private String datadisDistributorCode;
        private Integer datadisPointType;
        private Boolean datadisIsThirdParty;
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

        public Builder withUser(User user) {
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

        public Builder withDatadisValidDateFrom(LocalDate validDateFrom) {
            this.datadisValidDateFrom = validDateFrom;
            return this;
        }

        public Builder withDatadisDistributor(String distributor) {
            this.datadisDistributor = distributor;
            return this;
        }

        public Builder withDatadisDistributorCode(String distributorCode) {
            this.datadisDistributorCode = distributorCode;
            return this;
        }

        public Builder withDatadisPointType(Integer pointType) {
            this.datadisPointType = pointType;
            return this;
        }

        public Builder withDatadisIsThirdParty(Boolean isThirdParty) {
            this.datadisIsThirdParty = isThirdParty;
            return this;
        }

        public Builder withShellyId(String shellyId) {
            this.shellyId = shellyId;
            return this;
        }

        public Builder withShellyMac(String shellyMac) {
            this.shellyMac = shellyMac;
            return this;
        }

        public Builder withShellyMqttPrefix(String shellyMqttPrefix) {
            this.shellyMqttPrefix = shellyMqttPrefix;
            return this;
        }

        public Supply build() {
            return new Supply(this);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public Float getPartitionCoefficient() {
        return partitionCoefficient;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public LocalDate getDatadisValidDateFrom() {
        return datadisValidDateFrom;
    }

    public String getDatadisDistributor() {
        return datadisDistributor;
    }

    public String getDatadisDistributorCode() {
        return datadisDistributorCode;
    }

    public Integer getDatadisPointType() {
        return datadisPointType;
    }

    public Boolean getDatadisIsThirdParty() {
        return datadisIsThirdParty;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setDatadisValidDateFrom(LocalDate datadisValidDateFrom) {
        this.datadisValidDateFrom = datadisValidDateFrom;
    }

    public void setDatadisDistributor(String datadisDistributor) {
        this.datadisDistributor = datadisDistributor;
    }

    public void setDatadisDistributorCode(String datadisDistributorCode) {
        this.datadisDistributorCode = datadisDistributorCode;
    }

    public void setDatadisPointType(Integer datadisPointType) {
        this.datadisPointType = datadisPointType;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Supply supply)) return false;
        return Objects.equals(getId(), supply.getId()) && Objects.equals(getCode(), supply.getCode()) && Objects.equals(getUser(), supply.getUser());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getCode(), getUser());
    }
}