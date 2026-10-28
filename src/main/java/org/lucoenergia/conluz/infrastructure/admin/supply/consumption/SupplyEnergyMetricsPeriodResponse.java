package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * The period the metrics were actually computed over, half-open. Both bounds are null when the supply has no
 * stored consumption record and the request did not bound the period itself.
 */
@Schema(requiredProperties = {"startDate", "endDate"})
public class SupplyEnergyMetricsPeriodResponse {

    @Schema(description = "First instant included in the aggregation, inclusive",
            example = "2024-01-01T00:00:00+01:00", types = {"string", "null"})
    private final OffsetDateTime startDate;
    @Schema(description = "End of the aggregation, exclusive: the first instant not included",
            example = "2024-02-01T00:00:00+01:00", types = {"string", "null"})
    private final OffsetDateTime endDate;

    public SupplyEnergyMetricsPeriodResponse(OffsetDateTime startDate, OffsetDateTime endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public OffsetDateTime getStartDate() {
        return startDate;
    }

    public OffsetDateTime getEndDate() {
        return endDate;
    }
}
