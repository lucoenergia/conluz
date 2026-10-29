package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.consumption.SelfConsumptionRatioStatus;
import org.lucoenergia.conluz.infrastructure.shared.web.energy.SelfConsumptionRatioBasisResponse;

@Schema(requiredProperties = {"supply", "period", "coverage", "energy", "savings",
        "selfSufficiencyRatio", "selfConsumptionRatio", "selfConsumptionRatioStatus", "selfConsumptionRatioBasis"})
public class SupplyEnergyMetricsResponse {

    @Schema(description = "The supply the metrics were computed for.")
    private final SupplyEnergyMetricsSupplyResponse supply;
    @Schema(description = "The period the metrics were computed over.")
    private final SupplyEnergyMetricsPeriodResponse period;
    @Schema(description = "How much of the period is backed by stored records.")
    private final SupplyEnergyMetricsCoverageResponse coverage;
    @Schema(description = "The energy totals the ratios were derived from.")
    private final SupplyEnergyMetricsEnergyResponse energy;
    @Schema(description = "What the self-consumed energy of the period was worth.")
    private final SupplyEnergyMetricsSavingsResponse savings;
    @Schema(description = "Share of the energy consumed that came from the community rather than " +
            "from the grid, on a 0-1 scale. Null when nothing was consumed in the period.",
            example = "0.42", types = {"number", "null"})
    private final Double selfSufficiencyRatio;
    @Schema(description = "Share of the energy assigned to this supply that it consumed rather " +
            "than fed back to the grid, on a 0-1 scale. Null when nothing was assigned to the " +
            "supply in the period, and for a plant's own supply, to which it does not apply: " +
            "selfConsumptionRatioStatus says which.",
            example = "0.67", types = {"number", "null"})
    private final Double selfConsumptionRatio;
    @Schema(description = "Whether selfConsumptionRatio could be computed. AVAILABLE: it is. " +
            "NOTHING_ASSIGNED: no energy was assigned to the supplies it covers, which includes a period " +
            "without data. NOT_APPLICABLE: the supply is a plant's own supply, whose surplus is stored as production.",
            example = "AVAILABLE")
    private final SelfConsumptionRatioStatus selfConsumptionRatioStatus;
    @Schema(description = "The energy selfConsumptionRatio was computed from, and the plant's own supplies " +
            "it leaves out. Both figures are 0 when the status is NOT_APPLICABLE.")
    private final SelfConsumptionRatioBasisResponse selfConsumptionRatioBasis;

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
        this.savings = new SupplyEnergyMetricsSavingsResponse(metrics.getSavings());
        this.selfSufficiencyRatio = metrics.getSelfSufficiencyRatio();
        this.selfConsumptionRatio = metrics.getSelfConsumptionRatio().getValue();
        this.selfConsumptionRatioStatus = metrics.getSelfConsumptionRatio().getStatus();
        this.selfConsumptionRatioBasis = new SelfConsumptionRatioBasisResponse(metrics.getSelfConsumptionRatio());
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

    public SupplyEnergyMetricsSavingsResponse getSavings() {
        return savings;
    }

    public Double getSelfSufficiencyRatio() {
        return selfSufficiencyRatio;
    }

    public Double getSelfConsumptionRatio() {
        return selfConsumptionRatio;
    }

    public SelfConsumptionRatioStatus getSelfConsumptionRatioStatus() {
        return selfConsumptionRatioStatus;
    }

    public SelfConsumptionRatioBasisResponse getSelfConsumptionRatioBasis() {
        return selfConsumptionRatioBasis;
    }
}
