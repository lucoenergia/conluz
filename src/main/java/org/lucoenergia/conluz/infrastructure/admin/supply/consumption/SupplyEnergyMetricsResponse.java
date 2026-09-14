package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.SupplyEnergyMetrics;

@Schema(requiredProperties = {"supply", "period", "coverage", "energy", "selfSufficiencyRatio",
        "selfConsumptionRatio"})
public class SupplyEnergyMetricsResponse {

    @Schema(description = "The supply the metrics were computed for.")
    private final SupplyEnergyMetricsSupplyResponse supply;
    @Schema(description = "The period the metrics were computed over.")
    private final SupplyEnergyMetricsPeriodResponse period;
    @Schema(description = "How much of the period is backed by stored records.")
    private final SupplyEnergyMetricsCoverageResponse coverage;
    @Schema(description = "The energy totals the ratios were derived from.")
    private final SupplyEnergyMetricsEnergyResponse energy;
    @Schema(description = "Share of the energy consumed that came from the community rather than " +
            "from the grid, on a 0-1 scale. Null when nothing was consumed in the period.",
            example = "0.42", types = {"number", "null"})
    private final Double selfSufficiencyRatio;
    @Schema(description = "Share of the energy assigned to this supply that it consumed rather " +
            "than fed back to the grid, on a 0-1 scale. Null when nothing was assigned to the " +
            "supply in the period.",
            example = "0.67", types = {"number", "null"})
    private final Double selfConsumptionRatio;

    public SupplyEnergyMetricsResponse(SupplyEnergyMetrics metrics) {
        this.supply = new SupplyEnergyMetricsSupplyResponse(metrics.getSupply());
        this.period = new SupplyEnergyMetricsPeriodResponse(metrics.getStartDate(), metrics.getEndDate());
        this.coverage = new SupplyEnergyMetricsCoverageResponse(metrics.getHoursWithData(), metrics.getExpectedHours());
        this.energy = new SupplyEnergyMetricsEnergyResponse(
                metrics.getTotalConsumptionKWh(),
                metrics.getGridImportKWh(),
                metrics.getSelfConsumptionKWh(),
                metrics.getSurplusKWh(),
                metrics.getAssignedProductionKWh());
        this.selfSufficiencyRatio = metrics.getSelfSufficiencyRatio();
        this.selfConsumptionRatio = metrics.getSelfConsumptionRatio();
    }

    public SupplyEnergyMetricsSupplyResponse getSupply() {
        return supply;
    }

    public SupplyEnergyMetricsPeriodResponse getPeriod() {
        return period;
    }

    public SupplyEnergyMetricsCoverageResponse getCoverage() {
        return coverage;
    }

    public SupplyEnergyMetricsEnergyResponse getEnergy() {
        return energy;
    }

    public Double getSelfSufficiencyRatio() {
        return selfSufficiencyRatio;
    }

    public Double getSelfConsumptionRatio() {
        return selfConsumptionRatio;
    }
}
