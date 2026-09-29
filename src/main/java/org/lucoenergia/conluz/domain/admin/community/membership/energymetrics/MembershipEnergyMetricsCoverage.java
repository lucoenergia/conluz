package org.lucoenergia.conluz.domain.admin.community.membership.energymetrics;

import org.lucoenergia.conluz.domain.consumption.SupplyCoverage;

import java.util.List;

/**
 * How much of a period is backed by stored records, across every supply of a membership.
 *
 * <p>The expected hours are the hours of the period times the number of supplies, so one supply
 * without records lowers coverage even when the rest are complete. {@link #getSuppliesWithData()}
 * tells one silent supply apart from gaps spread across all of them.
 */
public class MembershipEnergyMetricsCoverage {

    private final long hoursWithData;
    private final long expectedHours;
    private final int supplyCount;
    private final int suppliesWithData;

    private MembershipEnergyMetricsCoverage(long hoursWithData, long expectedHours, int supplyCount,
                                            int suppliesWithData) {
        this.hoursWithData = hoursWithData;
        this.expectedHours = expectedHours;
        this.supplyCount = supplyCount;
        this.suppliesWithData = suppliesWithData;
    }

    /**
     * Adds up the coverage of every supply of the membership, each over the same period.
     */
    public static MembershipEnergyMetricsCoverage of(List<SupplyCoverage> supplies) {
        long hoursWithData = 0L;
        long expectedHours = 0L;
        int suppliesWithData = 0;
        for (SupplyCoverage supply : supplies) {
            hoursWithData += supply.getHoursWithData();
            expectedHours += supply.getExpectedHours();
            if (supply.getHoursWithData() > 0) {
                suppliesWithData++;
            }
        }
        return new MembershipEnergyMetricsCoverage(hoursWithData, expectedHours, supplies.size(),
                suppliesWithData);
    }

    /**
     * The coverage of a membership for which no period could be resolved: nothing expected and
     * nothing found, over all of its supplies.
     */
    public static MembershipEnergyMetricsCoverage none(int supplyCount) {
        return new MembershipEnergyMetricsCoverage(0L, 0L, supplyCount, 0);
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
}
