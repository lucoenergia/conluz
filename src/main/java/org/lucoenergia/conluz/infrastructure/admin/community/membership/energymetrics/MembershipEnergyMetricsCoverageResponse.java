package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How much of the period is backed by stored records, across every supply of the membership.
 * Hours without a record are left out of the sums rather than counted as zero. The two supply
 * counters tell one silent supply apart from gaps spread across all of them.
 */
@Schema(requiredProperties = {"hoursWithData", "expectedHours", "supplyCount", "suppliesWithData"})
public class MembershipEnergyMetricsCoverageResponse {

    @Schema(description = "Number of hourly consumption records found in the period, summed across " +
            "every supply of the membership", example = "1450")
    private final long hoursWithData;
    @Schema(description = "Number of hours the period spans, times the number of supplies of the " +
            "membership. A supply without any record therefore lowers coverage even when the rest " +
            "are complete. Accounts for daylight saving transitions.",
            example = "2232")
    private final long expectedHours;
    @Schema(description = "Number of supplies of the membership in this community", example = "3")
    private final int supplyCount;
    @Schema(description = "Number of those supplies with at least one hourly record in the period",
            example = "2")
    private final int suppliesWithData;

    public MembershipEnergyMetricsCoverageResponse(long hoursWithData, long expectedHours, int supplyCount,
                                                   int suppliesWithData) {
        this.hoursWithData = hoursWithData;
        this.expectedHours = expectedHours;
        this.supplyCount = supplyCount;
        this.suppliesWithData = suppliesWithData;
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
