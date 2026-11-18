package org.lucoenergia.conluz.infrastructure.admin.supply.energytoday;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.energytoday.SupplyProductionToday;

/**
 * Today's energy of a supply point. Only its production is reported.
 */
@Schema(requiredProperties = {"production"})
public class SupplyEnergyTodayResponse {

    @Schema(description = "Today's production assigned to the supply point, derived from the inverter snapshots of " +
            "the plants it has a partition coefficient in.")
    private final SupplyProductionTodayResponse production;

    public SupplyEnergyTodayResponse(SupplyProductionToday production) {
        this.production = new SupplyProductionTodayResponse(production);
    }

    public SupplyProductionTodayResponse getProduction() {
        return production;
    }
}
