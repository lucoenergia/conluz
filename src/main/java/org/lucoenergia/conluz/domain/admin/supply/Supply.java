package org.lucoenergia.conluz.domain.admin.supply;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.shared.uuid.ValidUUID;

import java.time.LocalDate;
import java.util.UUID;

public class Supply {

    @NotNull
    @ValidUUID
    private UUID id;
    @NotBlank
    private final String code;
    @NotNull
    private User user;
    private final String name;
    @NotBlank
    private final String address;
    @NotNull
    private final Float partitionCoefficient;
    @NotNull
    private Boolean enabled;
    private final LocalDate validDateFrom;
    private final String distributor;
    private final String distributorCode;
    private final String pointType;

    private Supply(Builder builder) {
        this.id = builder.id;
        this.code = builder.code;
        this.user = builder.user;
        this.name = builder.name;
        this.address = builder.address;
        this.partitionCoefficient = builder.partitionCoefficient;
        this.enabled = builder.enabled;
        this.validDateFrom = builder.validDateFrom;
        this.distributor = builder.distributor;
        this.distributorCode = builder.distributorCode;
        this.pointType = builder.pointType;
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
        private LocalDate validDateFrom;
        private String distributor;
        private String distributorCode;
        private String pointType;

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

        public Builder withPointType(String pointType) {
            this.pointType = pointType;
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

    public LocalDate getValidDateFrom() {
        return validDateFrom;
    }

    public String getDistributor() {
        return distributor;
    }

    public String getDistributorCode() {
        return distributorCode;
    }

    public String getPointType() {
        return pointType;
    }
}