package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyMetrics;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityEnergyMetricsService;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.infrastructure.admin.supply.consumption.EnergyPeriodApiDocs;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/energy-metrics",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetCommunityEnergyMetricsController {

    private final GetCommunityEnergyMetricsService service;

    public GetCommunityEnergyMetricsController(GetCommunityEnergyMetricsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the energy metrics of a community: its production and how it was used.",
            description = """
                    Aggregates the distributor's records of the whole community over one period. Only
                    community totals are reported: no figure, identifier or code of a single supply or
                    member.

                    - `energy.productionKWh`: what every plant of the community produced, as reported for
                      the plant's own supply.
                    - `energy.selfConsumptionKWh` and `energy.surplusKWh`: summed over every **enabled**
                      supply of the community, whatever the membership status of its owner. A plant's own
                      supply counts with its self-consumption; its surplus is stored as 0, since it is
                      reported as production.
                    - `selfConsumptionRatio` and `surplusRatio`: the shares of the assigned energy that
                      was used in the community and that went to the grid, on a 0-1 scale, computed
                      **once** from the summed self-consumption and surplus, so they add up to 1. Both
                      are `null` when both figures are zero or unknown, never `0`.
                    - `peakPowerKWp` and `nominalPowerKW`: the peak power of the modules and the power
                      that can be exported to the grid, each summed over every plant of the community.
                    - `surplusIncome.amountEur`: an **estimate** of what `energy.surplusKWh` earned at
                      OMIE day-ahead prices, the Spanish system's marginal price. Each hour's surplus is
                      priced at the mean of the prices stored for that hour (four quarter-hour prices, or a
                      single hourly one), summed unrounded and rounded once to cents. Market prices may be
                      zero or **negative** and are applied as they are, so the amount may be negative. No
                      taxes, fees, deviation costs or retailer margins are included.
                    - `surplusIncome.unpricedSurplusKWh`: the surplus of the hours without a stored price,
                      which add nothing to the amount. Both are `null` exactly when `energy.surplusKWh` is.
                    - `co2Avoided.amountKg`: an **estimate** of the CO₂ that `energy.productionKWh`
                      avoided, in kg CO₂e: what the same electricity would have emitted had it been bought
                      from a retailer without guarantees of origin, using the MITECO "Mix sin GdO" factors,
                      **not** the average grid mix. Each calendar year of the period, in the community's
                      time zone, uses its own factor, or the latest earlier one when its own is not
                      published yet; the products are summed unrounded and rounded once to 3 decimals.
                    - `co2Avoided.factorYears`: the years of the factors applied, in ascending order. Both
                      are `null` when `energy.productionKWh` is, or when a year with production is earlier
                      than every available factor.
                    - `plants`: every plant of the community with its peak and nominal power, its
                      production and its share of the community's production.

                    A figure nothing was stored for is `null`, never `0`: a plant without any production
                    record in the period has null production and share, and so do the energy totals
                    when nothing was stored for any plant or supply.

                    **Period:**
                    The period is resolved in exactly one of three ways, and the resolved bounds are
                    always reported in `period`, also when the caller supplied them, with an end after
                    the current hour clipped as described under **Period checks**.

                    - `startDate` and `endDate`: an explicit period. They must be supplied together, and
                      a `startDate` that is not before the `endDate` is a bad request.
                    - `period=LATEST_PUBLISHED_MONTH`: the most recent complete calendar month, in the
                      community's time zone, that the distributor has published for any supply of the
                      community, exactly as a membership's latest published month is resolved over the
                      member's supplies. An hourly record is published when it carries self-consumed
                      energy, zero included, and a month is published for a supply when at least
                      90% of the hours it could have published carry it. The search goes backwards
                      through the 24 complete months before the current one; the current month is never
                      a candidate. When no month in the search window is published, no period is
                      resolved. Combining it with `startDate` or `endDate` is a bad request.
                    - Neither: from the earliest record stored for any supply of the community to the end
                      of the hour of the latest.

                    When no period can be resolved, the response is still successful, with null period
                    bounds, null figures and ratios, no supply with data, and every plant listed with
                    its peak and nominal power alone.

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    **Coverage:**
                    `coverage.supplyCount` is the number of enabled supplies of the community, and
                    `coverage.suppliesWithData` how many of them have data for the period: their hourly
                    records carrying self-consumed energy cover at least 90% of the hours the period
                    spans, the same threshold a month is published by.

                    Readable by any active member of the community, whatever their role. A platform
                    admin who is not a member is answered 403, and every other caller who may not read
                    it 404.
                    """,
            tags = ApiTag.COMMUNITIES,
            operationId = "getCommunityEnergyMetrics",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Energy metrics retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadCommunityProduction(#communityId)")
    public CommunityEnergyMetricsResponse getCommunityEnergyMetrics(
            @PathVariable("communityId") UUID communityId,
            @Parameter(description = "Start of an explicit period, inclusive. Requires endDate.")
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of an explicit period, exclusive. Requires startDate.")
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @Parameter(description = "A period the server resolves by itself. Cannot be combined with " +
                    "startDate or endDate.")
            @RequestParam(value = "period", required = false) EnergyMetricsReferencePeriod period) {

        CommunityEnergyMetrics metrics = service.getEnergyMetrics(communityId, startDate, endDate, period);

        return new CommunityEnergyMetricsResponse(metrics);
    }
}
