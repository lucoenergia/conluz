package org.lucoenergia.conluz.domain.production;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.infrastructure.shared.uuid.ValidUUID;

import java.time.OffsetDateTime;
import java.util.UUID;

public class EnergyStation {

    @NotNull
    @ValidUUID
    private UUID id;
    @NotBlank
    private String name;
    @NotBlank
    private String code;
    @NotBlank
    private String address;
    @NotBlank
    private String description;
    @NotBlank
    private InverterProvider inverterProvider;
    /**
     * Represented using kWp
     */
    @NotNull
    private Double totalPower;
    private OffsetDateTime connectionDate;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
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

    public Double getTotalPower() {
        return totalPower;
    }

    public OffsetDateTime getConnectionDate() {
        return connectionDate;
    }

    public static class Builder {
        private UUID id;
        private String name;
        private String code;
        private String address;
        private String description;
        private InverterProvider inverterProvider;
        private Double totalPower;
        private OffsetDateTime connectionDate;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withCode(String code) {
            this.code = code;
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

        public Builder withTotalPower(Double totalPower) {
            this.totalPower = totalPower;
            return this;
        }

        public Builder withConnectionDate(OffsetDateTime connectionDate) {
            this.connectionDate = connectionDate;
            return this;
        }

        public EnergyStation build() {
            EnergyStation station = new EnergyStation();
            station.id = this.id;
            station.name = this.name;
            station.code = this.code;
            station.address = this.address;
            station.description = this.description;
            station.inverterProvider = this.inverterProvider;
            station.totalPower = this.totalPower;
            station.connectionDate = this.connectionDate;
            return station;
        }
    }
}
