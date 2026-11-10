package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How much of the period is backed by the plant's stored production records. A gap between the two
 * values means the period is not fully published; hours without a record are left out of the sum rather
 * than counted as zero, while a stored zero, such as at night, counts as a record.
 */
@Schema(requiredProperties = {"hoursWithData", "expectedHours"})
public class PlantEnergyMetricsCoverageResponse {

    @Schema(description = "Number of hourly production records found in the period, zeros included. 0 " +
            "when no period could be resolved.", example = "24")
    private final long hoursWithData;
    @Schema(description = "Number of hours the period spans. Accounts for daylight saving " +
            "transitions, so a transition day contributes 23 or 25 hours rather than 24. 0 when no period " +
            "could be resolved.",
            example = "24")
    private final long expectedHours;

    public PlantEnergyMetricsCoverageResponse(long hoursWithData, long expectedHours) {
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
