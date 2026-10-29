package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetrics;
import org.lucoenergia.conluz.domain.consumption.EnergyBalance;
import org.lucoenergia.conluz.domain.consumption.SelfConsumptionRatioStatus;
import org.lucoenergia.conluz.infrastructure.shared.web.energy.SelfConsumptionRatioBasisResponse;
import org.lucoenergia.conluz.infrastructure.admin.supply.consumption.SupplyEnergyMetricsPeriodResponse;

@Schema(requiredProperties = {"period", "coverage", "energy", "savings", "selfSufficiencyRatio",
        "selfConsumptionRatio", "selfConsumptionRatioStatus", "selfConsumptionRatioBasis"})
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
            "energy of the supplies that are no plant's own supply, which it leaves out. Null when " +
            "nothing was assigned to those supplies in the period, and when every supply is a plant's " +
            "own supply: selfConsumptionRatioStatus says which. selfConsumptionRatioBasis holds the " +
            "energy it was computed from.",
            example = "0.67", types = {"number", "null"})
    private final Double selfConsumptionRatio;
    @Schema(description = "Whether selfConsumptionRatio could be computed. AVAILABLE: it is. " +
            "NOTHING_ASSIGNED: no energy was assigned to the supplies it covers, which includes a period " +
            "without data. NOT_APPLICABLE: every supply of the membership is a plant's own supply.",
            example = "AVAILABLE")
    private final SelfConsumptionRatioStatus selfConsumptionRatioStatus;
    @Schema(description = "The energy selfConsumptionRatio was computed from, and the plant's own supplies " +
            "it leaves out. Both figures are 0 when the status is NOT_APPLICABLE.")
    private final SelfConsumptionRatioBasisResponse selfConsumptionRatioBasis;

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
        this.selfConsumptionRatio = metrics.getSelfConsumptionRatio().getValue();
        this.selfConsumptionRatioStatus = metrics.getSelfConsumptionRatio().getStatus();
        this.selfConsumptionRatioBasis = new SelfConsumptionRatioBasisResponse(metrics.getSelfConsumptionRatio());
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

    public SelfConsumptionRatioStatus getSelfConsumptionRatioStatus() {
        return selfConsumptionRatioStatus;
    }

    public SelfConsumptionRatioBasisResponse getSelfConsumptionRatioBasis() {
        return selfConsumptionRatioBasis;
    }
}
