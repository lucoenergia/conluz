package org.lucoenergia.conluz.domain.admin.supply;

import org.lucoenergia.conluz.domain.admin.user.User;

import java.time.LocalDate;

public class Supply {

    private final String id;
    private User user;
    private final String name;
    private final String address;
    private final Float partitionCoefficient;
    private final Boolean enabled;
    private final LocalDate validDateFrom;
    private final String distributor;
    private final String distributorCode;
    private final String pointType;

    private Supply(Builder builder) {
        this.id = builder.id;
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

    public static class Builder {
        private String id;
        private User user;
        private String name;
        private String address;
        private Float partitionCoefficient;
        private Boolean enabled;
        private LocalDate validDateFrom;
        private String distributor;
        private String distributorCode;
        private String pointType;

        public Builder withId(String id) {
            this.id = id;
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

    public String getId() {
        return id;
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