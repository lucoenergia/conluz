package org.lucoenergia.conluz.infrastructure.production.plant;

import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;

import java.time.LocalDate;
import java.util.UUID;

public class PlantResponse {

    private final UUID id;
    private final String code;
    private final UserResponse user;
    private final String name;
    private final String address;
    private final String description;
    private final InverterProvider inverterProvider;
    private final Double totalPower;
    private final LocalDate connectionDate;

    public PlantResponse(Plant plant) {
        this.id = plant.getId();
        this.code = plant.getCode();
        this.user = new UserResponse(plant.getUser());
        this.name = plant.getName();
        this.address = plant.getAddress();
        this.description = plant.getDescription();
        this.inverterProvider = plant.getInverterProvider();
        this.totalPower = plant.getTotalPower();
        this.connectionDate = plant.getConnectionDate();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public UserResponse getUser() {
        return user;
    }

    public String getName() {
        return name;
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
}
