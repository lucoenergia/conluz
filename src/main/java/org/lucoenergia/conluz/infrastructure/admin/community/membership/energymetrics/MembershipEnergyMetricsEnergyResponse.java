package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.consumption.EnergyBalance;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhRounding;

/**
 * The energy totals of every supply of the membership over the period, in kWh. gridImportKWh,
 * selfConsumptionKWh and surplusKWh are the stored values summed; totalConsumptionKWh and
 * assignedProductionKWh are derived from them.
 *
 * <p>Every figure is rounded here to 3 decimals, once, from its unrounded value: a derived total is
 * rounded after summing, not summed from the rounded figures, and the ratios are computed from the
 * unrounded figures too.
 */
@Schema(requiredProperties = {"totalConsumptionKWh", "gridImportKWh", "selfConsumptionKWh",
        "surplusKWh", "assignedProductionKWh"})
public class MembershipEnergyMetricsEnergyResponse {

    @Schema(description = "Energy consumed, whatever its origin: gridImportKWh plus " +
            "selfConsumptionKWh. In kWh, rounded to 3 decimals after summing, so it can differ by " +
            "0.001 from the sum of the rounded figures.",
            example = "1155.0")
    private final double totalConsumptionKWh;
    @Schema(description = "Energy taken from the grid by every supply of the membership over the " +
            "period. Does not include self-consumed energy. In kWh, rounded to 3 decimals.",
            example = "670.0")
    private final double gridImportKWh;
    @Schema(description = "Energy produced by the community and consumed by every supply of the " +
            "membership over the period, as reported by the distributor. In kWh, rounded to 3 " +
            "decimals.",
            example = "485.0")
    private final double selfConsumptionKWh;
    @Schema(description = "Energy assigned to every supply of the membership and fed back to the " +
            "grid over the period. In kWh, rounded to 3 decimals.",
            example = "239.0")
    private final double surplusKWh;
    @Schema(description = "Energy assigned to every supply of the membership: selfConsumptionKWh plus " +
            "surplusKWh. In kWh, rounded to 3 decimals after summing, so it can differ by 0.001 from " +
            "the sum of the rounded figures.",
            example = "724.0")
    private final double assignedProductionKWh;

    public MembershipEnergyMetricsEnergyResponse(EnergyBalance energyBalance) {
        this.totalConsumptionKWh = KWhRounding.round(energyBalance.getTotalConsumptionKWh());
        this.gridImportKWh = KWhRounding.round(energyBalance.getGridImportKWh());
        this.selfConsumptionKWh = KWhRounding.round(energyBalance.getSelfConsumptionKWh());
        this.surplusKWh = KWhRounding.round(energyBalance.getSurplusKWh());
        this.assignedProductionKWh = KWhRounding.round(energyBalance.getAssignedProductionKWh());
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
