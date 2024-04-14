package org.lucoenergia.conluz.infrastructure.production.plant;

import jakarta.persistence.*;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "plants")
public class PlantEntity {

    @Id
    private UUID id;
    private String name;
    private String code;
    @ManyToOne(fetch = FetchType.LAZY)
    private UserEntity user;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
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
        return Objects.equals(getId(), that.getId()) && Objects.equals(getName(), that.getName()) && Objects.equals(getCode(), that.getCode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), getCode());
    }


    public static class Builder {

        private UUID id;
        private String name;
        private String code;
        private UserEntity user;
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

        public Builder withCode(String code) {
            this.code = code;
            return this;
        }

        public Builder withUser(UserEntity user) {
            this.user = user;
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
            plantEntity.setCode(code);
            plantEntity.setUser(user);
            plantEntity.setAddress(address);
            plantEntity.setDescription(description);
            plantEntity.setInverterProvider(inverterProvider);
            plantEntity.setTotalPower(totalPower);
            plantEntity.setConnectionDate(connectionDate);

            return plantEntity;
        }
    }
}
