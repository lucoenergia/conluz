package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;

/**
 * How many supplies of the community are in scope, and how many of them have data for the period.
 */
@Schema(requiredProperties = {"supplyCount", "suppliesWithData"})
public class CommunityEnergyMetricsCoverageResponse {

    @Schema(description = "Number of enabled supplies of the community, whatever the membership status " +
            "of their owners", example = "12")
    private final int supplyCount;
    @Schema(description = "Number of those supplies with data for the period: whose hourly records " +
            "carrying self-consumed energy cover at least " + ReferenceMonthResolver.PUBLISHED_HOURS_MIN_PERCENT +
            "% of the hours the period spans. Measured against a threshold, whereas the energy metrics of a " +
            "membership count a supply with at least one hourly record, so the two figures are not " +
            "comparable across endpoints. 0 when no period could be resolved.",
            example = "11")
    private final int suppliesWithData;

    public CommunityEnergyMetricsCoverageResponse(int supplyCount, int suppliesWithData) {
        this.supplyCount = supplyCount;
        this.suppliesWithData = suppliesWithData;
    }

    public int getSupplyCount() {
        return supplyCount;
    }

    public int getSuppliesWithData() {
        return suppliesWithData;
    }
}
