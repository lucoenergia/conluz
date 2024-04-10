package org.lucoenergia.conluz.infrastructure.production;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.lucoenergia.conluz.domain.production.InverterProvider;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity(name = "energy_stations")
public class EnergyStationEntity {

    @Id
    private UUID id;
    private String name;
    private String code;
    private String address;
    private String description;
    private InverterProvider inverterProvider;
    /**
     * Represented using kWp
     */
    private Double totalPower;
    private OffsetDateTime connectionDate;

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

    public OffsetDateTime getConnectionDate() {
        return connectionDate;
    }

    public void setConnectionDate(OffsetDateTime conectionDate) {
        this.connectionDate = conectionDate;
    }
}
