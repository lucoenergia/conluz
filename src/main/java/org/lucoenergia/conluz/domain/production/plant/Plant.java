package org.lucoenergia.conluz.domain.production.plant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.shared.uuid.ValidUUID;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Plant {

    @NotNull
    @ValidUUID
    private UUID id;
    @NotBlank
    private String name;
    /**
     * The plant identifier assigned by the inverter provider (currently Huawei). Used verbatim as
     * the {@code station_code} tag in InfluxDB: this is the join key between the PostgreSQL plant
     * row and its time series. It is not a CUPS and not a CAU -- the regulator's code is
     * {@code regulatory_code}.
     */
    @NotBlank
    private String providerCode;
    /**
     * The identifier assigned by the regulator. In Spain this is the CAU (Código de Autoconsumo).
     * It is not the provider's station code ({@code provider_code}) and not a CUPS.
     */
    private String regulatoryCode;
    /**
     * The CIL (Código de la Instalación de producción a efectos de Liquidación): the settlement code of the
     * installation assigned by the distribution company. Null when the plant has none recorded. See {@link PlantCil}.
     */
    private String cil;
    @NotBlank
    private String address;
    @NotBlank
    private String description;
    @NotBlank
    private InverterProvider inverterProvider;
    /**
     * Peak power of the plant's modules, in kWp.
     */
    @NotNull
    private Double peakPowerKWp;
    /**
     * Power the plant can export to the grid, in kW, as capped by its inverters and grid connection.
     */
    @NotNull
    private Double nominalPowerKW;
    private LocalDate connectionDate;
    @NotNull
    private Supply supply;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getRegulatoryCode() {
        return regulatoryCode;
    }

    public String getCil() {
        return cil;
    }

    /**
     * Replaces the CIL with its normalised form (PLT-004).
     *
     * @throws PlantCilInvalidException when the CIL, once normalised, is not one
     */
    public void normaliseCil() {
        cil = PlantCil.normalise(cil);
    }

    public String getAddress() {
        return address;
    }

    public String getDescription() {
        return description;
    }

    public InverterProvider getInverterProvider() {
        return inverterProvider;
    }

    public Double getPeakPowerKWp() {
        return peakPowerKWp;
    }

    public Double getNominalPowerKW() {
        return nominalPowerKW;
    }

    public LocalDate getConnectionDate() {
        return connectionDate;
    }

    public Supply getSupply() {
        return supply;
    }

    public void setSupply(Supply supply) {
        this.supply = supply;
    }

    public void initializeUuid() {
        id = UUID.randomUUID();
    }

    public Community getCommunity() {
        return supply.getCommunity();
    }

    public static class Builder {
        private UUID id;
        private String name;
        private String providerCode;
        private String regulatoryCode;
        private String cil;
        private String address;
        private String description;
        private InverterProvider inverterProvider;
        private Double peakPowerKWp;
        private Double nominalPowerKW;
        private LocalDate connectionDate;
        private Supply supply;

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

        public Builder withRegulatoryCode(String regulatoryCode) {
            this.regulatoryCode = regulatoryCode;
            return this;
        }

        public Builder withCil(String cil) {
            this.cil = cil;
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

        public Builder withInverterProvider(InverterProvider provider) {
            this.inverterProvider = provider;
            return this;
        }

        public Builder withPeakPowerKWp(Double peakPowerKWp) {
            this.peakPowerKWp = peakPowerKWp;
            return this;
        }

        public Builder withNominalPowerKW(Double nominalPowerKW) {
            this.nominalPowerKW = nominalPowerKW;
            return this;
        }

        public Builder withConnectionDate(LocalDate connectionDate) {
            this.connectionDate = connectionDate;
            return this;
        }

        public Builder withSupply(Supply supply) {
            this.supply = supply;
            return this;
        }

        public Plant build() {
            Plant station = new Plant();
            station.id = this.id;
            station.name = this.name;
            station.providerCode = this.providerCode;
            station.regulatoryCode = this.regulatoryCode;
            station.cil = this.cil;
            station.address = this.address;
            station.description = this.description;
            station.inverterProvider = this.inverterProvider;
            station.peakPowerKWp = this.peakPowerKWp;
            station.nominalPowerKW = this.nominalPowerKW;
            station.connectionDate = this.connectionDate;
            station.supply = this.supply;
            return station;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Plant plant)) return false;
        return Objects.equals(getId(), plant.getId()) && Objects.equals(getName(), plant.getName()) && Objects.equals(getProviderCode(), plant.getProviderCode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), getProviderCode());
    }

    @Override
    public String toString() {
        return "Plant{" +
                "name='" + name + '\'' +
                ", providerCode='" + providerCode + '\'' +
                ", id=" + id +
                '}';
    }
}
