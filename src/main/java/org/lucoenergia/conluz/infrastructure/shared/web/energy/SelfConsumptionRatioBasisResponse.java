package org.lucoenergia.conluz.infrastructure.shared.web.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.consumption.SelfConsumptionRatio;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhRounding;

/**
 * The energy a self-consumption ratio was computed from, and the plant's own supplies it leaves out
 * (SEM-004, ENM-008). A client shows the ratio's "X of Y kWh" line from it and never recomputes the
 * ratio.
 *
 * <p>Both figures are rounded here to 3 decimals, once, from their unrounded values, as every other
 * kWh figure is (EVL-001); the ratio was computed from the unrounded ones.</p>
 */
@Schema(requiredProperties = {"selfConsumptionKWh", "assignedProductionKWh", "excludedPlantSupplyCount"})
public class SelfConsumptionRatioBasisResponse {

    @Schema(description = "Energy produced by the community and consumed by the supplies the ratio " +
            "covers over the period, as reported by the distributor: the ratio's numerator. In kWh, " +
            "rounded to 3 decimals.",
            example = "485.0")
    private final double selfConsumptionKWh;
    @Schema(description = "Energy assigned to the supplies the ratio covers: selfConsumptionKWh plus " +
            "surplus over the period, and the ratio's denominator. In kWh, rounded to 3 decimals after " +
            "summing.",
            example = "724.0")
    private final double assignedProductionKWh;
    @Schema(description = "How many supplies that are a plant's own supply the ratio leaves out. Their energy is in " +
            "neither figure above.",
            example = "1")
    private final int excludedPlantSupplyCount;

    public SelfConsumptionRatioBasisResponse(SelfConsumptionRatio ratio) {
        this.selfConsumptionKWh = KWhRounding.round(ratio.getSelfConsumptionKWh());
        this.assignedProductionKWh = KWhRounding.round(ratio.getAssignedProductionKWh());
        this.excludedPlantSupplyCount = ratio.getExcludedPlantSupplyCount();
    }

    public double getSelfConsumptionKWh() {
        return selfConsumptionKWh;
    }

    public double getAssignedProductionKWh() {
        return assignedProductionKWh;
    }

    public int getExcludedPlantSupplyCount() {
        return excludedPlantSupplyCount;
    }
}
