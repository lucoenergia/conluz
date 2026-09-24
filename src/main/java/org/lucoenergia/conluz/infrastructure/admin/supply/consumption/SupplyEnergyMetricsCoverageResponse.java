package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How much of the period is backed by stored records. A gap between the two values means the
 * period is not fully synchronised; hours without a record are left out of the sums rather than
 * counted as zero.
 */
@Schema(requiredProperties = {"hoursWithData", "expectedHours"})
public class SupplyEnergyMetricsCoverageResponse {

    @Schema(description = "Number of hourly consumption records found in the period", example = "3721")
    private final long hoursWithData;
    @Schema(description = "Number of hours the period spans. Accounts for daylight saving " +
            "transitions, so a transition day contributes 23 or 25 hours rather than 24.",
            example = "3744")
    private final long expectedHours;

    public SupplyEnergyMetricsCoverageResponse(long hoursWithData, long expectedHours) {
        this.hoursWithData = hoursWithData;
        this.expectedHours = expectedHours;
    }

    public long getHoursWithData() {
        return hoursWithData;
    }

    public long getExpectedHours() {
        return expectedHours;
    }
}
