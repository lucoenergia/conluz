package org.lucoenergia.conluz.domain.admin.community.membership.energymetrics;

import org.lucoenergia.conluz.domain.consumption.EnergyBalance;
import org.lucoenergia.conluz.domain.consumption.SupplyCoverage;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The energy metrics of every supply of a membership over one resolved period, added up.
 *
 * <p>The per-supply figures are the ones the per-supply metrics compute; nothing here is
 * recomputed from records. The energy totals are summed and the two ratios are derived once from
 * those sums, never averaged from the per-supply ratios: a supply consuming 100 kWh and one
 * consuming 1 kWh must not weigh the same.
 *
 * <p>Coverage spans every supply of the membership, as {@link MembershipEnergyMetricsCoverage}
 * computes it.
 *
 * <p>Savings are null when no period could be resolved, and a figure whenever one was -- zero when
 * nothing was priced.
 */
public class MembershipEnergyMetrics {

    private final OffsetDateTime startDate;
    private final OffsetDateTime endDate;
    private final EnergyBalance energyBalance;
    private final MembershipEnergyMetricsCoverage coverage;
    private final SupplySavings savings;

    private MembershipEnergyMetrics(OffsetDateTime startDate, OffsetDateTime endDate, EnergyBalance energyBalance,
                                    MembershipEnergyMetricsCoverage coverage, SupplySavings savings) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.energyBalance = energyBalance;
        this.coverage = coverage;
        this.savings = savings;
    }

    /**
     * Adds up the metrics of every supply of the membership, each computed over the same period.
     * No supplies at all is a period in which nothing was recorded or priced: zero totals, null
     * ratios, no expected hours and savings of zero.
     */
    public static MembershipEnergyMetrics of(OffsetDateTime startDate, OffsetDateTime endDate,
                                             List<SupplyEnergyMetrics> supplies) {
        EnergyBalance energyBalance = EnergyBalance.empty();
        for (SupplyEnergyMetrics supply : supplies) {
            energyBalance = energyBalance.plus(supply.getEnergyBalance());
        }
        MembershipEnergyMetricsCoverage coverage = MembershipEnergyMetricsCoverage.of(supplies.stream()
                .map(supply -> new SupplyCoverage(supply.getHoursWithData(), supply.getExpectedHours()))
                .toList());
        SupplySavings savings = SupplySavings.total(supplies.stream().map(SupplyEnergyMetrics::getSavings).toList());

        return new MembershipEnergyMetrics(startDate, endDate, energyBalance, coverage, savings);
    }

    /**
     * The metrics of a membership for which no period could be resolved: null bounds, zero
     * totals, null ratios and {@link SupplySavings#unpriced() unpriced} savings.
     */
    public static MembershipEnergyMetrics withoutPeriod(int supplyCount) {
        return new MembershipEnergyMetrics(null, null, EnergyBalance.empty(),
                MembershipEnergyMetricsCoverage.none(supplyCount), SupplySavings.unpriced());
    }

    /**
     * The first instant of the period, inclusive, or null when no period could be resolved.
     */
    public OffsetDateTime getStartDate() {
        return startDate;
    }

    /**
     * The last instant of the period, inclusive, or null when no period could be resolved.
     */
    public OffsetDateTime getEndDate() {
        return endDate;
    }

    public EnergyBalance getEnergyBalance() {
        return energyBalance;
    }

    public long getHoursWithData() {
        return coverage.getHoursWithData();
    }

    public long getExpectedHours() {
        return coverage.getExpectedHours();
    }

    public int getSupplyCount() {
        return coverage.getSupplyCount();
    }

    public int getSuppliesWithData() {
        return coverage.getSuppliesWithData();
    }

    public SupplySavings getSavings() {
        return savings;
    }
}
