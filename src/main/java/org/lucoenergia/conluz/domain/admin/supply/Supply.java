package org.lucoenergia.conluz.domain.admin.supply;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.lucoenergia.conluz.domain.admin.supply.contract.SupplyContract;
import org.lucoenergia.conluz.domain.admin.supply.datadis.SupplyDatadis;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.shelly.SupplyShelly;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.shared.uuid.ValidUUID;

import java.util.Objects;
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
    private String address;
    private String addressRef;
    @NotNull
    @PositiveOrZero
    private final Float partitionCoefficient;
    @NotNull
    private Boolean enabled;

    private SupplyContract contract;
    private SupplyDistributor distributor;
    private SupplyDatadis datadis;
    private SupplyShelly shelly;

    private Supply(Builder builder) {
        this.id = builder.id;
        this.code = builder.code;
        this.user = builder.user;
        this.name = builder.name;
        this.address = builder.address;
        this.addressRef = builder.addressRef;
        this.partitionCoefficient = builder.partitionCoefficient == null ? 0F : builder.partitionCoefficient;
        this.enabled = builder.enabled != null && builder.enabled;
        this.contract = builder.contract;
        this.distributor = builder.distributor;
        this.datadis = builder.datadis;
        this.shelly = builder.shelly;
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
        private String addressRef;
        private Float partitionCoefficient;
        private Boolean enabled;
        private SupplyContract contract;
        private SupplyDistributor distributor;
        private SupplyDatadis datadis;
        private SupplyShelly shelly;

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

        public Builder withAddressRef(String addressRef) {
            this.addressRef = addressRef;
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

        public Builder withContract(SupplyContract contract) {
            this.contract = contract;
            return this;
        }

        public Builder withDistributor(SupplyDistributor distributor) {
            this.distributor = distributor;
            return this;
        }

        public Builder withDatadis(SupplyDatadis datadis) {
            this.datadis = datadis;
            return this;
        }

        public Builder withShelly(SupplyShelly shelly) {
            this.shelly = shelly;
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

    public String getAddressRef() {
        return addressRef;
    }

    public Float getPartitionCoefficient() {
        return partitionCoefficient;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public SupplyContract getContract() {
        return contract;
    }

    public void setContract(SupplyContract contract) {
        this.contract = contract;
    }

    public SupplyDistributor getDistributor() {
        return distributor;
    }

    public void setDistributor(SupplyDistributor distributor) {
        this.distributor = distributor;
    }

    public SupplyDatadis getDatadis() {
        return datadis;
    }

    public void setDatadis(SupplyDatadis datadis) {
        this.datadis = datadis;
    }

    public SupplyShelly getShelly() {
        return shelly;
    }

    public void setShelly(SupplyShelly shelly) {
        this.shelly = shelly;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setAddressRef(String addressRef) {
        this.addressRef = addressRef;
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

    public Builder copy() {
        return new Builder()
                .withId(this.id)
                .withCode(this.code)
                .withUser(this.user)
                .withName(this.name)
                .withAddress(this.address)
                .withAddressRef(this.addressRef)
                .withPartitionCoefficient(this.partitionCoefficient)
                .withEnabled(this.enabled)
                .withContract(this.contract)
                .withDistributor(this.distributor)
                .withDatadis(this.datadis)
                .withShelly(this.shelly);
    }
}
