package org.lucoenergia.conluz.domain.admin.supply;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents supply information from the external Datadis API.
 * The annotation ignores unknown properties to ensure resilience when the API adds new fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DatadisSupply {

    private String address;
    private String cups;
    private String postalCode;
    private String province;
    private String municipality;
    private String validDateFrom;
    private String validDateTo;
    private Integer pointType;
    private String distributor;
    private String distributorCode;

    public DatadisSupply() {
    }

    private DatadisSupply(Builder builder) {
        this.address = builder.address;
        this.cups = builder.cups;
        this.postalCode = builder.postalCode;
        this.province = builder.province;
        this.municipality = builder.municipality;
        this.validDateFrom = builder.validDateFrom;
        this.validDateTo = builder.validDateTo;
        this.pointType = builder.pointType;
        this.distributor = builder.distributor;
        this.distributorCode = builder.distributorCode;
    }

    public static class Builder {
        private String address;
        private String cups;
        private String postalCode;
        private String province;
        private String municipality;
        private String validDateFrom;
        private String validDateTo;
        private Integer pointType;
        private String distributor;
        private String distributorCode;

        public Builder withAddress(String address) {
            this.address = address;
            return this;
        }

        public Builder withCups(String cups) {
            this.cups = cups;
            return this;
        }

        public Builder withPostalCode(String postalCode) {
            this.postalCode = postalCode;
            return this;
        }

        public Builder withProvince(String province) {
            this.province = province;
            return this;
        }

        public Builder withMunicipality(String municipality) {
            this.municipality = municipality;
            return this;
        }

        public Builder withValidDateFrom(String validDateFrom) {
            this.validDateFrom = validDateFrom;
            return this;
        }

        public Builder withValidDateTo(String validDateTo) {
            this.validDateTo = validDateTo;
            return this;
        }

        public Builder withPointType(Integer pointType) {
            this.pointType = pointType;
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

        public DatadisSupply build() {
            return new DatadisSupply(this);
        }
    }

    public String getAddress() {
        return address;
    }

    public String getCups() {
        return cups;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getProvince() {
        return province;
    }

    public String getMunicipality() {
        return municipality;
    }

    public String getValidDateFrom() {
        return validDateFrom;
    }

    public String getValidDateTo() {
        return validDateTo;
    }

    public Integer getPointType() {
        return pointType;
    }

    public String getDistributor() {
        return distributor;
    }

    public String getDistributorCode() {
        return distributorCode;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setCups(String cups) {
        this.cups = cups;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public void setMunicipality(String municipality) {
        this.municipality = municipality;
    }

    public void setValidDateFrom(String validDateFrom) {
        this.validDateFrom = validDateFrom;
    }

    public void setValidDateTo(String validDateTo) {
        this.validDateTo = validDateTo;
    }

    public void setPointType(Integer pointType) {
        this.pointType = pointType;
    }

    public void setDistributor(String distributor) {
        this.distributor = distributor;
    }

    public void setDistributorCode(String distributorCode) {
        this.distributorCode = distributorCode;
    }
}