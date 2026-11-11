package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyMetrics;
import org.lucoenergia.conluz.infrastructure.admin.supply.consumption.SupplyEnergyMetricsPeriodResponse;

import java.util.List;

@Schema(requiredProperties = {"period", "energy", "selfConsumptionRatio", "surplusRatio", "peakPowerKWp",
        "nominalPowerKW", "surplusIncome", "co2Avoided", "coverage", "plants"})
public class CommunityEnergyMetricsResponse {

    @Schema(description = "The period the metrics were computed over, the same for every supply and plant.")
    private final SupplyEnergyMetricsPeriodResponse period;
    @Schema(description = "The energy totals of the community: its plants' production, and the " +
            "self-consumption and surplus of every supply of the community, summed.")
    private final CommunityEnergyMetricsEnergyResponse energy;
    @Schema(description = "Share of the energy assigned to the community's supplies that they consumed " +
            "rather than fed back to the grid, on a 0-1 scale, computed once from the summed " +
            "self-consumption and surplus of every supply of the community, plant's own supplies " +
            "included. Adds up to 1 with surplusRatio. Null when both are zero or unknown.",
            example = "0.82", types = {"number", "null"})
    private final Double selfConsumptionRatio;
    @Schema(description = "Share of the energy assigned to the community's supplies that was fed back to " +
            "the grid, on a 0-1 scale, computed once from the summed self-consumption and surplus of every " +
            "supply of the community. Adds up to 1 with selfConsumptionRatio. Null when both are zero or " +
            "unknown.",
            example = "0.18", types = {"number", "null"})
    private final Double surplusRatio;
    @Schema(description = "Peak power of every plant of the community, summed, in kWp.", example = "200.0")
    private final double peakPowerKWp;
    @Schema(description = "Power every plant of the community can export to the grid, summed, in kW.",
            example = "160.0")
    private final double nominalPowerKW;
    @Schema(description = "An estimate of what the community's surplus earned at OMIE day-ahead prices " +
            "over the period, and the surplus that could not be priced. Computed over exactly the surplus " +
            "reported in energy.surplusKWh.")
    private final CommunityEnergyMetricsSurplusIncomeResponse surplusIncome;
    @Schema(description = "An estimate of the CO₂ the community's plants avoided over the period, " +
            "computed from exactly the production reported in energy.productionKWh with the MITECO " +
            "\"Mix sin GdO\" electricity emission factors, and the years of the factors applied.")
    private final CommunityEnergyMetricsCo2AvoidedResponse co2Avoided;
    @Schema(description = "How many supplies of the community have data for the period.")
    private final CommunityEnergyMetricsCoverageResponse coverage;
    @Schema(description = "Every plant of the community, ordered by name, with its production over the " +
            "period.")
    private final List<CommunityPlantEnergyResponse> plants;

    public CommunityEnergyMetricsResponse(CommunityEnergyMetrics metrics) {
        this.period = new SupplyEnergyMetricsPeriodResponse(metrics.getStartDate(), metrics.getEndDate());
        this.energy = new CommunityEnergyMetricsEnergyResponse(metrics);
        this.selfConsumptionRatio = metrics.getSelfConsumptionRatio();
        this.surplusRatio = metrics.getSurplusRatio();
        this.peakPowerKWp = metrics.getPeakPowerKWp();
        this.nominalPowerKW = metrics.getNominalPowerKW();
        this.surplusIncome = new CommunityEnergyMetricsSurplusIncomeResponse(metrics);
        this.co2Avoided = new CommunityEnergyMetricsCo2AvoidedResponse(metrics);
        this.coverage = new CommunityEnergyMetricsCoverageResponse(metrics.getSupplyCount(),
                metrics.getSuppliesWithData());
        this.plants = metrics.getPlants().stream().map(CommunityPlantEnergyResponse::new).toList();
    }

    public SupplyEnergyMetricsPeriodResponse getPeriod() {
        return period;
    }

    public CommunityEnergyMetricsEnergyResponse getEnergy() {
        return energy;
    }

    public Double getSelfConsumptionRatio() {
        return selfConsumptionRatio;
    }

    public Double getSurplusRatio() {
        return surplusRatio;
    }

    public double getPeakPowerKWp() {
        return peakPowerKWp;
    }

    public double getNominalPowerKW() {
        return nominalPowerKW;
    }

    public CommunityEnergyMetricsSurplusIncomeResponse getSurplusIncome() {
        return surplusIncome;
    }

    public CommunityEnergyMetricsCo2AvoidedResponse getCo2Avoided() {
        return co2Avoided;
    }

    public CommunityEnergyMetricsCoverageResponse getCoverage() {
        return coverage;
    }

    public List<CommunityPlantEnergyResponse> getPlants() {
        return plants;
    }
}
