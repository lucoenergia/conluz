package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityMonthlyEnergySeriesService;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/energy-series/monthly",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetCommunityMonthlyEnergySeriesController {

    private final GetCommunityMonthlyEnergySeriesService service;

    public GetCommunityMonthlyEnergySeriesController(GetCommunityMonthlyEnergySeriesService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the energy of a community, added up per month.",
            description = """
                    For each month of the requested period, the self-consumption and surplus of every
                    **enabled** supply of the community with data in it, whatever the membership status
                    of its owner, added up. Only community totals are reported: no figure, identifier or
                    code of a single supply or member. Each energy figure sums the unrounded figures and
                    is rounded once, to 3 decimals, on the result.

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    **Every month is emitted:**
                    A bucket is returned for every month whose local midnight on day 1 falls inside the
                    period, in chronological order, whether or not any supply has data that month. The
                    figures are read from the monthly pre-aggregates, refreshed daily for the months of
                    the Datadis sync window, which cover the whole month.

                    **Completeness:**
                    `supplyCount` is the number of enabled supplies of the community, the same in every
                    bucket, and `suppliesWithData` how many of them have data in the month. A month with
                    `suppliesWithData` below `supplyCount` is incomplete.

                    **Gaps and measured zeros:**
                    A supply has data in a month when it stored a monthly record for it. The
                    monthly records keep no count of the hours carrying self-consumed energy, so the
                    90% threshold of the daily and hourly series is not applied, and a month aggregated
                    before the distributor published its self-consumed energy reads as `0`. A month in
                    which no supply stored a monthly record has `null` energy figures and a
                    `suppliesWithData` of 0, never zeros.

                    Readable by any active member of the community, whatever their role. A platform
                    admin who is not a member is answered 403, and every other caller who may not read
                    it 404.
                    """,
            tags = ApiTag.COMMUNITIES,
            operationId = "getCommunityMonthlyEnergySeries",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Monthly energy series retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadCommunityProduction(#communityId)")
    public List<CommunityMonthlyEnergyBucketResponse> getCommunityMonthlyEnergySeries(
            @PathVariable("communityId") UUID communityId,
            @Parameter(description = "Start of the period, inclusive.")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of the period, exclusive.")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return service.getMonthlySeries(communityId, startDate, endDate)
                .stream()
                .map(CommunityMonthlyEnergyBucketResponse::new)
                .toList();
    }
}
