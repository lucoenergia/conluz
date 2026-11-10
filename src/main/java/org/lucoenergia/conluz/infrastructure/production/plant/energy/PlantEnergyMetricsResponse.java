package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyMetrics;
import org.lucoenergia.conluz.infrastructure.admin.supply.consumption.SupplyEnergyMetricsPeriodResponse;

@Schema(requiredProperties = {"period", "energy", "coverage"})
public class PlantEnergyMetricsResponse {

    @Schema(description = "The period the metrics were computed over.")
    private final SupplyEnergyMetricsPeriodResponse period;
    @Schema(description = "The energy totals of the plant: its production.")
    private final PlantEnergyMetricsEnergyResponse energy;
    @Schema(description = "How much of the period is backed by the plant's stored production records.")
    private final PlantEnergyMetricsCoverageResponse coverage;

    public PlantEnergyMetricsResponse(PlantEnergyMetrics metrics) {
        this.period = new SupplyEnergyMetricsPeriodResponse(
                metrics.getPeriod().map(EnergyMetricsPeriod::getStartDate).orElse(null),
                metrics.getPeriod().map(EnergyMetricsPeriod::getEndDate).orElse(null));
        this.energy = new PlantEnergyMetricsEnergyResponse(metrics.getProductionKWh());
        this.coverage = new PlantEnergyMetricsCoverageResponse(metrics.getHoursWithData(),
                metrics.getExpectedHours());
    }

    public SupplyEnergyMetricsPeriodResponse getPeriod() {
        return period;
    }

    public PlantEnergyMetricsEnergyResponse getEnergy() {
        return energy;
    }

    public PlantEnergyMetricsCoverageResponse getCoverage() {
        return coverage;
    }
}
