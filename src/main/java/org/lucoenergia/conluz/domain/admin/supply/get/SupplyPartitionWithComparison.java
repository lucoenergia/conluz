package org.lucoenergia.conluz.domain.admin.supply.get;

import java.util.UUID;

public class SupplyPartitionWithComparison {

    private final UUID supplyId;
    private final String cups;
    private final String supplyName;
    private final String address;
    private final String userFullName;
    private final double coefficient;
    private final Double previousCoefficient;

    private SupplyPartitionWithComparison(Builder builder) {
        this.supplyId = builder.supplyId;
        this.cups = builder.cups;
        this.supplyName = builder.supplyName;
        this.address = builder.address;
        this.userFullName = builder.userFullName;
        this.coefficient = builder.coefficient;
        this.previousCoefficient = builder.previousCoefficient;
    }

    public UUID getSupplyId() {
        return supplyId;
    }

    public String getCups() {
        return cups;
    }

    public String getSupplyName() {
        return supplyName;
    }

    public String getAddress() {
        return address;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public double getCoefficient() {
        return coefficient;
    }

    public Double getPreviousCoefficient() {
        return previousCoefficient;
    }

    public static class Builder {
        private UUID supplyId;
        private String cups;
        private String supplyName;
        private String address;
        private String userFullName;
        private double coefficient;
        private Double previousCoefficient;

        public Builder withSupplyId(UUID supplyId) {
            this.supplyId = supplyId;
            return this;
        }

        public Builder withCups(String cups) {
            this.cups = cups;
            return this;
        }

        public Builder withSupplyName(String supplyName) {
            this.supplyName = supplyName;
            return this;
        }

        public Builder withAddress(String address) {
            this.address = address;
            return this;
        }

        public Builder withUserFullName(String userFullName) {
            this.userFullName = userFullName;
            return this;
        }

        public Builder withCoefficient(double coefficient) {
            this.coefficient = coefficient;
            return this;
        }

        public Builder withPreviousCoefficient(Double previousCoefficient) {
            this.previousCoefficient = previousCoefficient;
            return this;
        }

        public SupplyPartitionWithComparison build() {
            return new SupplyPartitionWithComparison(this);
        }
    }
}
