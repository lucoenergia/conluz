package org.lucoenergia.conluz.domain.production.plant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.infrastructure.shared.uuid.ValidUUID;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Plant {

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
    private LocalDate connectionDate;
    @NotNull
    private User user;

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

    public LocalDate getConnectionDate() {
        return connectionDate;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void initializeUuid() {
        id = UUID.randomUUID();
    }

    public static class Builder {
        private UUID id;
        private String name;
        private String code;
        private String address;
        private String description;
        private InverterProvider inverterProvider;
        private Double totalPower;
        private LocalDate connectionDate;
        private User user;

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

        public Builder withConnectionDate(LocalDate connectionDate) {
            this.connectionDate = connectionDate;
            return this;
        }

        public Builder withUser(User user) {
            this.user = user;
            return this;
        }

        public Plant build() {
            Plant station = new Plant();
            station.id = this.id;
            station.name = this.name;
            station.code = this.code;
            station.address = this.address;
            station.description = this.description;
            station.inverterProvider = this.inverterProvider;
            station.totalPower = this.totalPower;
            station.connectionDate = this.connectionDate;
            station.user = this.user;
            return station;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Plant plant)) return false;
        return Objects.equals(getId(), plant.getId()) && Objects.equals(getName(), plant.getName()) && Objects.equals(getCode(), plant.getCode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), getCode());
    }

    @Override
    public String toString() {
        return "Plant{" +
                "name='" + name + '\'' +
                ", code='" + code + '\'' +
                ", id=" + id +
                '}';
    }
}
