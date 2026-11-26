package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimAvailableMonths;

import java.time.YearMonth;

/**
 * The range of months a plant's surplus can be claimed for.
 */
@Schema(requiredProperties = {"firstMonth", "latestPublishedMonth"})
public class PlantSurplusClaimAvailableMonthsResponse {

    @Schema(description = "Month of the plant's earliest applied coefficient, as YYYY-MM, in the community's time " +
            "zone. Null when the plant has no applied coefficient.",
            example = "2025-03", types = {"string", "null"})
    private final String firstMonth;
    @Schema(description = "Latest month, as YYYY-MM, whose assigned production Datadis has published for any supply " +
            "that ever held an applied coefficient of the plant, the plant's own supply excluded. Null when there " +
            "is none.",
            example = "2026-09", types = {"string", "null"})
    private final String latestPublishedMonth;

    public PlantSurplusClaimAvailableMonthsResponse(SurplusClaimAvailableMonths months) {
        this.firstMonth = format(months.firstMonth());
        this.latestPublishedMonth = format(months.latestPublishedMonth());
    }

    static String format(YearMonth month) {
        return month == null ? null : month.toString();
    }

    public String getFirstMonth() {
        return firstMonth;
    }

    public String getLatestPublishedMonth() {
        return latestPublishedMonth;
    }
}
