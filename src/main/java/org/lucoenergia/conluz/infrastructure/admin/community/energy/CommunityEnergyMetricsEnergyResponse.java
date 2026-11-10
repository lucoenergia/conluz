package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyMetrics;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhRounding;

/**
 * The energy totals of the community over the period, in kWh, each rounded here to 3 decimals, once,
 * from its unrounded value. A figure nothing was stored for is null, never 0.
 */
@Schema(requiredProperties = {"productionKWh", "selfConsumptionKWh", "surplusKWh"})
public class CommunityEnergyMetricsEnergyResponse {

    @Schema(description = "Energy the community's plants exported to the grid at their connection points " +
            "over the period, as measured by the distributor for each plant's own supply (net of the plants' " +
            "own consumption). In kWh, rounded to 3 decimals after summing. Null when no plant stored a " +
            "production record in the period, or when no period could be resolved.",
            example = "1520.0", types = {"number", "null"})
    private final Double productionKWh;
    @Schema(description = "Energy produced by the community and consumed by every supply of the " +
            "community over the period, as reported by the distributor. In kWh, rounded to 3 decimals. " +
            "Null when no supply stored a record in the period, or when no period could be resolved.",
            example = "1210.0", types = {"number", "null"})
    private final Double selfConsumptionKWh;
    @Schema(description = "Energy assigned to every supply of the community and fed back to the grid " +
            "over the period. In kWh, rounded to 3 decimals. Null when no supply stored a record in the " +
            "period, or when no period could be resolved.",
            example = "260.0", types = {"number", "null"})
    private final Double surplusKWh;

    public CommunityEnergyMetricsEnergyResponse(CommunityEnergyMetrics metrics) {
        this.productionKWh = round(metrics.getProductionKWh());
        this.selfConsumptionKWh = round(metrics.getSelfConsumptionKWh());
        this.surplusKWh = round(metrics.getSurplusKWh());
    }

    static Double round(Double kWh) {
        return kWh == null ? null : KWhRounding.round(kWh);
    }

    public Double getProductionKWh() {
        return productionKWh;
    }

    public Double getSelfConsumptionKWh() {
        return selfConsumptionKWh;
    }

    public Double getSurplusKWh() {
        return surplusKWh;
    }
}
