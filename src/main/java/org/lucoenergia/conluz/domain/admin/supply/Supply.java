package org.lucoenergia.conluz.domain.admin.supply;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.datadis.DistributorCode;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.shared.uuid.ValidUUID;

import java.time.LocalDate;
import java.util.UUID;

public class Supply {

    private static final Integer DEFAULT_POINT_TYPE = 5;
    private static final String DEFAULT_DISTRIBUTOR_CODE = DistributorCode.E_DISTRIBUCION;

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
    private LocalDate validDateFrom;
    private String distributor;
    private String distributorCode;
    private Integer pointType;

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
        private Integer pointType;

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

        public Builder withPointType(Integer pointType) {
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
        return distributorCode != null ? distributorCode : DEFAULT_DISTRIBUTOR_CODE;
    }

    public Integer getPointType() {
        return pointType != null ? pointType : DEFAULT_POINT_TYPE;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setValidDateFrom(LocalDate validDateFrom) {
        this.validDateFrom = validDateFrom;
    }

    public void setDistributor(String distributor) {
        this.distributor = distributor;
    }

    public void setDistributorCode(String distributorCode) {
        this.distributorCode = distributorCode;
    }

    public void setPointType(Integer pointType) {
        this.pointType = pointType;
    }
}