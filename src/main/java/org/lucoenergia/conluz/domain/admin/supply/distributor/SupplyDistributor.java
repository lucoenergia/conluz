package org.lucoenergia.conluz.domain.admin.supply.distributor;

public class SupplyDistributor {

    private String name;
    private String code;
    private Integer pointType;

    public SupplyDistributor() {
    }

    private SupplyDistributor(Builder builder) {
        this.name = builder.name;
        this.code = builder.code;
        this.pointType = builder.pointType;
    }

    public static class Builder {
        private String name;
        private String code;
        private Integer pointType;

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

        public SupplyDistributor build() {
            return new SupplyDistributor(this);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getPointType() {
        return pointType;
    }

    public void setPointType(Integer pointType) {
        this.pointType = pointType;
    }
}
