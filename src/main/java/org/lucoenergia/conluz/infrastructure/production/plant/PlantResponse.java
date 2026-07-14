package org.lucoenergia.conluz.infrastructure.production.plant;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;

import java.time.LocalDate;
import java.util.UUID;

public class PlantResponse {

    private final UUID id;
    @Schema(description = "The plant identifier assigned by the inverter provider (currently Huawei). " +
            "Used verbatim as the station_code tag in InfluxDB: this is the join key between the " +
            "PostgreSQL plant row and its time series. It is not a CUPS and not a CAU -- the " +
            "regulator's code is regulatory_code.")
    private final String providerCode;
    private final SupplyResponse supply;
    private final String name;
    private final String address;
    private final String description;
    private final InverterProvider inverterProvider;
    private final Double totalPower;
    private final LocalDate connectionDate;

    public PlantResponse(Plant plant) {
        this.id = plant.getId();
        this.providerCode = plant.getProviderCode();
        this.supply = new SupplyResponse(plant.getSupply());
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

    public String getProviderCode() {
        return providerCode;
    }

    public SupplyResponse getSupply() {
        return supply;
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
