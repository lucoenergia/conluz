package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhRounding;

/**
 * The energy totals of the plant over the period, in kWh, rounded here to 3 decimals, once, from the
 * unrounded value. A figure nothing was stored for is null, never 0.
 */
@Schema(requiredProperties = {"productionKWh"})
public class PlantEnergyMetricsEnergyResponse {

    @Schema(description = "Energy the plant produced over the period, as reported by the distributor for " +
            "the plant's own supply. In kWh, rounded to 3 decimals. Null when the plant stored no " +
            "production record in the period, which is not a measured zero, or when no period could be " +
            "resolved.",
            example = "152.4", types = {"number", "null"})
    private final Double productionKWh;

    public PlantEnergyMetricsEnergyResponse(Double productionKWh) {
        this.productionKWh = round(productionKWh);
    }

    static Double round(Double kWh) {
        return kWh == null ? null : KWhRounding.round(kWh);
    }

    public Double getProductionKWh() {
        return productionKWh;
    }
}
