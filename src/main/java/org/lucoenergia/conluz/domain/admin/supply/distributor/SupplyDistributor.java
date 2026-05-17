package org.lucoenergia.conluz.domain.admin.supply.distributor;

public class SupplyDistributor {

    private final String name;
    private final String code;
    private final Integer pointType;

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

    public String getCode() {
        return code;
    }

    public Integer getPointType() {
        return pointType;
    }
}
