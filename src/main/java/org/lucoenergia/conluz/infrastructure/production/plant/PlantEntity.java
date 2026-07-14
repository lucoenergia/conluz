package org.lucoenergia.conluz.infrastructure.production.plant;

import jakarta.persistence.*;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "plants")
public class PlantEntity {

    @Id
    private UUID id;
    private String name;
    /**
     * The plant identifier assigned by the inverter provider (currently Huawei). Used verbatim as
     * the {@code station_code} tag in InfluxDB: this is the join key between the PostgreSQL plant
     * row and its time series. It is not a CUPS and not a CAU -- the regulator's code is
     * {@code regulatory_code}.
     */
    @Column(name = "provider_code")
    private String providerCode;
    @ManyToOne(fetch = FetchType.LAZY)
    private SupplyEntity supply;
    private String address;
    private String description;
    @Enumerated(EnumType.STRING)
    private InverterProvider inverterProvider;
    /**
     * Represented using kWp
     */
    private Double totalPower;
    private LocalDate connectionDate;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public SupplyEntity getSupply() {
        return supply;
    }

    public void setSupply(SupplyEntity supply) {
        this.supply = supply;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public InverterProvider getInverterProvider() {
        return inverterProvider;
    }

    public void setInverterProvider(InverterProvider provider) {
        this.inverterProvider = provider;
    }

    public Double getTotalPower() {
        return totalPower;
    }

    public void setTotalPower(Double totalPower) {
        this.totalPower = totalPower;
    }

    public LocalDate getConnectionDate() {
        return connectionDate;
    }

    public void setConnectionDate(LocalDate conectionDate) {
        this.connectionDate = conectionDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlantEntity that)) return false;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getName(), that.getName()) && Objects.equals(getProviderCode(), that.getProviderCode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), getProviderCode());
    }


    public static class Builder {

        private UUID id;
        private String name;
        private String providerCode;
        private SupplyEntity supply;
        private String address;
        private String description;
        private InverterProvider inverterProvider;
        private Double totalPower;
        private LocalDate connectionDate;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withProviderCode(String providerCode) {
            this.providerCode = providerCode;
            return this;
        }

        public Builder withSupply(SupplyEntity supply) {
            this.supply = supply;
            return this;
        }

        public Builder withAddress(String address) {
            this.address = address;
            return this;
        }

        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder withInverterProvider(InverterProvider inverterProvider) {
            this.inverterProvider = inverterProvider;
            return this;
        }

        public Builder withTotalPower(Double totalPower) {
            this.totalPower = totalPower;
            return this;
        }

        public Builder withConnectionDate(LocalDate connectionDate) {
            this.connectionDate = connectionDate;
            return this;
        }

        public PlantEntity build() {
            PlantEntity plantEntity = new PlantEntity();

            plantEntity.setId(id);
            plantEntity.setName(name);
            plantEntity.setProviderCode(providerCode);
            plantEntity.setSupply(supply);
            plantEntity.setAddress(address);
            plantEntity.setDescription(description);
            plantEntity.setInverterProvider(inverterProvider);
            plantEntity.setTotalPower(totalPower);
            plantEntity.setConnectionDate(connectionDate);

            return plantEntity;
        }
    }
}
