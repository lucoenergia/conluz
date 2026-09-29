package org.lucoenergia.conluz.domain.admin.community.membership.energymetrics;

import org.lucoenergia.conluz.domain.consumption.EnergyBalance;
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
 * <p>Coverage spans every supply of the membership: the expected hours are the hours of the period
 * times the number of supplies, so one supply without records lowers coverage even when the rest
 * are complete. {@link #getSuppliesWithData()} tells one silent supply apart from gaps spread
 * across all of them.
 *
 * <p>Savings are null when no period could be resolved, and a figure whenever one was -- zero when
 * nothing was priced.
 */
public class MembershipEnergyMetrics {

    private final OffsetDateTime startDate;
    private final OffsetDateTime endDate;
    private final EnergyBalance energyBalance;
    private final long hoursWithData;
    private final long expectedHours;
    private final int supplyCount;
    private final int suppliesWithData;
    private final SupplySavings savings;

    private MembershipEnergyMetrics(OffsetDateTime startDate, OffsetDateTime endDate, EnergyBalance energyBalance,
                                    long hoursWithData, long expectedHours, int supplyCount,
                                    int suppliesWithData, SupplySavings savings) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.energyBalance = energyBalance;
        this.hoursWithData = hoursWithData;
        this.expectedHours = expectedHours;
        this.supplyCount = supplyCount;
        this.suppliesWithData = suppliesWithData;
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
        long hoursWithData = 0L;
        long expectedHours = 0L;
        int suppliesWithData = 0;
        for (SupplyEnergyMetrics supply : supplies) {
            energyBalance = energyBalance.plus(supply.getEnergyBalance());
            hoursWithData += supply.getHoursWithData();
            expectedHours += supply.getExpectedHours();
            if (supply.getHoursWithData() > 0) {
                suppliesWithData++;
            }
        }
        SupplySavings savings = SupplySavings.total(supplies.stream().map(SupplyEnergyMetrics::getSavings).toList());

        return new MembershipEnergyMetrics(startDate, endDate, energyBalance, hoursWithData, expectedHours,
                supplies.size(), suppliesWithData, savings);
    }

    /**
     * The metrics of a membership for which no period could be resolved: null bounds, zero
     * totals, null ratios and {@link SupplySavings#unpriced() unpriced} savings.
     */
    public static MembershipEnergyMetrics withoutPeriod(int supplyCount) {
        return new MembershipEnergyMetrics(null, null, EnergyBalance.empty(), 0L, 0L, supplyCount, 0,
                SupplySavings.unpriced());
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
        return hoursWithData;
    }

    public long getExpectedHours() {
        return expectedHours;
    }

    public int getSupplyCount() {
        return supplyCount;
    }

    public int getSuppliesWithData() {
        return suppliesWithData;
    }

    public SupplySavings getSavings() {
        return savings;
    }
}
