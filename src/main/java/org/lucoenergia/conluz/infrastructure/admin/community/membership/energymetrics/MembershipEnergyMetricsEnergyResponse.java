package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.consumption.EnergyBalance;

/**
 * The energy totals of every supply of the membership over the period, in kWh. gridImportKWh,
 * selfConsumptionKWh and surplusKWh are the stored values summed; totalConsumptionKWh and
 * assignedProductionKWh are derived from them.
 */
@Schema(requiredProperties = {"totalConsumptionKWh", "gridImportKWh", "selfConsumptionKWh",
        "surplusKWh", "assignedProductionKWh"})
public class MembershipEnergyMetricsEnergyResponse {

    @Schema(description = "Energy consumed, whatever its origin: gridImportKWh plus selfConsumptionKWh",
            example = "1155.0")
    private final double totalConsumptionKWh;
    @Schema(description = "Energy taken from the grid. Does not include self-consumed energy.",
            example = "670.0")
    private final double gridImportKWh;
    @Schema(description = "Energy produced by the community and consumed by the membership's supplies, " +
            "as reported by the distributor", example = "485.0")
    private final double selfConsumptionKWh;
    @Schema(description = "Energy assigned to the membership's supplies and fed back to the grid",
            example = "239.0")
    private final double surplusKWh;
    @Schema(description = "Energy assigned to the membership's supplies: selfConsumptionKWh plus surplusKWh",
            example = "724.0")
    private final double assignedProductionKWh;

    public MembershipEnergyMetricsEnergyResponse(EnergyBalance energyBalance) {
        this.totalConsumptionKWh = energyBalance.getTotalConsumptionKWh();
        this.gridImportKWh = energyBalance.getGridImportKWh();
        this.selfConsumptionKWh = energyBalance.getSelfConsumptionKWh();
        this.surplusKWh = energyBalance.getSurplusKWh();
        this.assignedProductionKWh = energyBalance.getAssignedProductionKWh();
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
