package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The energy totals over the period, in kWh. gridImportKWh, selfConsumptionKWh and surplusKWh are
 * the stored values summed; totalConsumptionKWh and assignedProductionKWh are derived from them.
 */
@Schema(requiredProperties = {"totalConsumptionKWh", "gridImportKWh", "selfConsumptionKWh",
        "surplusKWh", "assignedProductionKWh"})
public class SupplyEnergyMetricsEnergyResponse {

    @Schema(description = "Energy consumed, whatever its origin: gridImportKWh plus selfConsumptionKWh",
            example = "1155.0")
    private final double totalConsumptionKWh;
    @Schema(description = "Energy taken from the grid. Does not include self-consumed energy.",
            example = "670.0")
    private final double gridImportKWh;
    @Schema(description = "Energy produced by the community and consumed by this supply, as " +
            "reported by the distributor", example = "485.0")
    private final double selfConsumptionKWh;
    @Schema(description = "Energy assigned to this supply and fed back to the grid", example = "239.0")
    private final double surplusKWh;
    @Schema(description = "Energy assigned to this supply: selfConsumptionKWh plus surplusKWh",
            example = "724.0")
    private final double assignedProductionKWh;

    public SupplyEnergyMetricsEnergyResponse(double totalConsumptionKWh, double gridImportKWh,
                                             double selfConsumptionKWh, double surplusKWh,
                                             double assignedProductionKWh) {
        this.totalConsumptionKWh = totalConsumptionKWh;
        this.gridImportKWh = gridImportKWh;
        this.selfConsumptionKWh = selfConsumptionKWh;
        this.surplusKWh = surplusKWh;
        this.assignedProductionKWh = assignedProductionKWh;
    }

    public double getTotalConsumptionKWh() {
        return totalConsumptionKWh;
    }

    public double getGridImportKWh() {
        return gridImportKWh;
    }

    public double getSelfConsumptionKWh() {
        return selfConsumptionKWh;
    }

    public double getSurplusKWh() {
        return surplusKWh;
    }

    public double getAssignedProductionKWh() {
        return assignedProductionKWh;
    }
}
