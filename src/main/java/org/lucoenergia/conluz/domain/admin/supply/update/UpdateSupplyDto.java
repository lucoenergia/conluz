package org.lucoenergia.conluz.domain.admin.supply.update;

import org.lucoenergia.conluz.domain.admin.supply.Supply;

public class UpdateSupplyDto {

    private final String code;
    private final String name;
    private final String address;
    private final String addressRef;
    private final Float partitionCoefficient;

    private UpdateSupplyDto(Builder builder) {
        this.code = builder.code;
        this.name = builder.name;
        this.address = builder.address;
        this.addressRef = builder.addressRef;
        this.partitionCoefficient = builder.partitionCoefficient;
    }

    public static class Builder {
        private String code;
        private String name;
        private String address;
        private String addressRef;
        private Float partitionCoefficient;

        public Builder() {
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder addressRef(String addressRef) {
            this.addressRef = addressRef;
            return this;
        }

        public Builder partitionCoefficient(Float partitionCoefficient) {
            this.partitionCoefficient = partitionCoefficient;
            return this;
        }

        public UpdateSupplyDto build() {
            return new UpdateSupplyDto(this);
        }
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getAddressRef() {
        return addressRef;
    }

    public Float getPartitionCoefficient() {
        return partitionCoefficient;
    }

    public Supply mapToSupply(Supply.Builder supplyBuilder) {
        supplyBuilder
                .withCode(code)
                .withName(name)
                .withAddress(address)
                .withAddressRef(addressRef)
                .withPartitionCoefficient(partitionCoefficient);
        return supplyBuilder.build();
    }
}
