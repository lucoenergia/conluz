package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityPlantEnergy;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.PlantReferenceResponse;

/**
 * One plant of the community over the period of the energy metrics.
 */
@Schema(requiredProperties = {"plant", "installedPowerKWp", "productionKWh", "productionShare"})
public class CommunityPlantEnergyResponse {

    @Schema(description = "The plant.")
    private final PlantReferenceResponse plant;
    @Schema(description = "Installed power of the plant, in kWp.", example = "100.0")
    private final double installedPowerKWp;
    @Schema(description = "Energy the plant exported to the grid at its connection point over the period, " +
            "as measured by the distributor for the plant's own supply (net of the plant's own consumption). " +
            "In kWh, rounded to 3 decimals. Null when the plant stored no production record in the period, " +
            "which is not a measured zero, or when no period could be resolved.",
            example = "760.0", types = {"number", "null"})
    private final Double productionKWh;
    @Schema(description = "Share of the community's production that came from this plant, on a 0-1 " +
            "scale, computed from the unrounded figures. Null when productionKWh is null, and when the " +
            "community produced nothing in the period.",
            example = "0.5", types = {"number", "null"})
    private final Double productionShare;

    public CommunityPlantEnergyResponse(CommunityPlantEnergy plantEnergy) {
        this.plant = new PlantReferenceResponse(plantEnergy.getPlant().id(), plantEnergy.getPlant().name());
        this.installedPowerKWp = plantEnergy.getInstalledPowerKWp();
        this.productionKWh = CommunityEnergyMetricsEnergyResponse.round(plantEnergy.getProductionKWh());
        this.productionShare = plantEnergy.getProductionShare();
    }

    public PlantReferenceResponse getPlant() {
        return plant;
    }

    public double getInstalledPowerKWp() {
        return installedPowerKWp;
    }

    public Double getProductionKWh() {
        return productionKWh;
    }

    public Double getProductionShare() {
        return productionShare;
    }
}
