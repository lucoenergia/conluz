package org.lucoenergia.conluz.infrastructure.production.plant;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.PlantCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SupplyReferenceResponse;

import java.time.LocalDate;
import java.util.UUID;

@Schema(requiredProperties = {"id", "providerCode", "regulatoryCode", "supply", "name", "address",
        "description", "inverterProvider", "totalPower", "connectionDate", "community", "capabilities"})
public class PlantResponse {

    private final UUID id;
    @Schema(description = "The plant identifier assigned by the inverter provider (currently Huawei). " +
            "Used verbatim as the station_code tag in InfluxDB: this is the join key between the " +
            "PostgreSQL plant row and its time series. It is not a CUPS and not a CAU -- the " +
            "regulator's code is regulatory_code.")
    private final String providerCode;
    @Schema(description = "The identifier assigned by the regulator. In Spain this is the CAU " +
            "(Codigo de Autoconsumo). It is not the provider's station code (provider_code) and " +
            "not a CUPS.", types = {"string", "null"})
    private final String regulatoryCode;
    @Schema(description = "The supply this plant produces onto. A reference: the full supply, "
            + "including its owner, is fetched from GET /supplies/{supplyId}, which not every "
            + "caller who may list plants is allowed to call.")
    private final SupplyReferenceResponse supply;
    private final String name;
    private final String address;
    @Schema(types = {"string", "null"})
    private final String description;
    private final InverterProvider inverterProvider;
    private final Double totalPower;
    @Schema(types = {"string", "null"})
    private final LocalDate connectionDate;
    @Schema(description = "The community that owns the plant.")
    private final PlantCommunityResponse community;

    @Schema(description = "What the caller may do with this plant.")
    private final PlantCapabilitiesResponse capabilities;

    public PlantResponse(Plant plant, PlantCapabilitiesResponse capabilities) {
        this.id = plant.getId();
        this.providerCode = plant.getProviderCode();
        this.regulatoryCode = plant.getRegulatoryCode();
        this.supply = new SupplyReferenceResponse(plant.getSupply().getId(), plant.getSupply().getCode(),
                plant.getSupply().getName());
        this.name = plant.getName();
        this.address = plant.getAddress();
        this.description = plant.getDescription();
        this.inverterProvider = plant.getInverterProvider();
        this.totalPower = plant.getTotalPower();
        this.connectionDate = plant.getConnectionDate();
        this.community = new PlantCommunityResponse(plant.getCommunity().getId());
        this.capabilities = capabilities;
    }

    public UUID getId() {
        return id;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getRegulatoryCode() {
        return regulatoryCode;
    }

    public SupplyReferenceResponse getSupply() {
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

    public PlantCommunityResponse getCommunity() {
        return community;
    }

    public PlantCapabilitiesResponse getCapabilities() {
        return capabilities;
    }
}
