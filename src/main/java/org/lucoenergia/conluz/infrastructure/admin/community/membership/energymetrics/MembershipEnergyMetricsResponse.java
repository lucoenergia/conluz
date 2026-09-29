package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetrics;
import org.lucoenergia.conluz.domain.consumption.EnergyBalance;
import org.lucoenergia.conluz.infrastructure.admin.supply.consumption.SupplyEnergyMetricsPeriodResponse;

@Schema(requiredProperties = {"period", "coverage", "energy", "savings", "selfSufficiencyRatio",
        "selfConsumptionRatio"})
public class MembershipEnergyMetricsResponse {

    @Schema(description = "The period the metrics were computed over, the same for every supply.")
    private final SupplyEnergyMetricsPeriodResponse period;
    @Schema(description = "How much of the period is backed by stored records, across every supply " +
            "of the membership.")
    private final MembershipEnergyMetricsCoverageResponse coverage;
    @Schema(description = "The energy totals of every supply of the membership, summed.")
    private final MembershipEnergyMetricsEnergyResponse energy;
    @Schema(description = "What the self-consumed energy of the membership's supplies was worth over " +
            "the period.")
    private final MembershipEnergyMetricsSavingsResponse savings;
    @Schema(description = "Share of the energy the membership's supplies consumed that came from the " +
            "community rather than from the grid, on a 0-1 scale, computed from the summed totals. " +
            "Null when nothing was consumed in the period.",
            example = "0.42", types = {"number", "null"})
    private final Double selfSufficiencyRatio;
    @Schema(description = "Share of the energy assigned to the membership's supplies that they " +
            "consumed rather than fed back to the grid, on a 0-1 scale, computed from the summed " +
            "totals. Null when nothing was assigned to them in the period.",
            example = "0.67", types = {"number", "null"})
    private final Double selfConsumptionRatio;

    public MembershipEnergyMetricsResponse(MembershipEnergyMetrics metrics) {
        EnergyBalance energyBalance = metrics.getEnergyBalance();
        this.period = new SupplyEnergyMetricsPeriodResponse(metrics.getStartDate(), metrics.getEndDate());
        this.coverage = new MembershipEnergyMetricsCoverageResponse(
                metrics.getHoursWithData(),
                metrics.getExpectedHours(),
                metrics.getSupplyCount(),
                metrics.getSuppliesWithData());
        this.energy = new MembershipEnergyMetricsEnergyResponse(energyBalance);
        this.savings = new MembershipEnergyMetricsSavingsResponse(metrics.getSavings());
        this.selfSufficiencyRatio = energyBalance.getSelfSufficiencyRatio();
        this.selfConsumptionRatio = energyBalance.getSelfConsumptionRatio();
    }

    public SupplyEnergyMetricsPeriodResponse getPeriod() {
        return period;
    }

    public MembershipEnergyMetricsCoverageResponse getCoverage() {
        return coverage;
    }

    public MembershipEnergyMetricsEnergyResponse getEnergy() {
        return energy;
    }

    public MembershipEnergyMetricsSavingsResponse getSavings() {
        return savings;
    }

    public Double getSelfSufficiencyRatio() {
        return selfSufficiencyRatio;
    }

    public Double getSelfConsumptionRatio() {
        return selfConsumptionRatio;
    }
}
