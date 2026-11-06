package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityDailyEnergySeriesService;
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
        value = "/api/v1/communities/{communityId}/energy-series/daily",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetCommunityDailyEnergySeriesController {

    private final GetCommunityDailyEnergySeriesService service;

    public GetCommunityDailyEnergySeriesController(GetCommunityDailyEnergySeriesService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the energy of a community, added up per day.",
            description = """
                    For each day of the requested period, the self-consumption and surplus of every
                    **enabled** supply of the community with data in it, whatever the membership status
                    of its owner, added up. Only community totals are reported: no figure, identifier or
                    code of a single supply or member. Each energy figure sums the unrounded figures and
                    is rounded once, to 3 decimals, on the result.

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    **Every day is emitted:**
                    A bucket is returned for every local day of the community's time zone that overlaps
                    the period, in chronological order, whether or not any supply has data that day. A
                    day the period starts or ends inside counts only its part inside the period.

                    **Completeness:**
                    `supplyCount` is the number of enabled supplies of the community, the same in every
                    bucket, and `suppliesWithData` how many of them have data in the day. A day with
                    `suppliesWithData` below `supplyCount` is incomplete.

                    **Gaps and measured zeros:**
                    A supply has data on a day when its hourly records carrying self-consumed
                    energy, zero included, cover at least 90% of the day's hours inside the period, the
                    same threshold a month is published by. Only those supplies are added up. A day in
                    which no supply has data has `null` energy figures and a `suppliesWithData` of 0,
                    never zeros; a day whose supplies with data carry no energy is a measured zero, `0`.

                    Readable by any active member of the community, whatever their role. A platform
                    admin who is not a member is answered 403, and every other caller who may not read
                    it 404.
                    """,
            tags = ApiTag.COMMUNITIES,
            operationId = "getCommunityDailyEnergySeries",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Daily energy series retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadCommunityProduction(#communityId)")
    public List<CommunityDailyEnergyBucketResponse> getCommunityDailyEnergySeries(
            @PathVariable("communityId") UUID communityId,
            @Parameter(description = "Start of the period, inclusive.")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of the period, exclusive.")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return service.getDailySeries(communityId, startDate, endDate)
                .stream()
                .map(CommunityDailyEnergyBucketResponse::new)
                .toList();
    }
}
