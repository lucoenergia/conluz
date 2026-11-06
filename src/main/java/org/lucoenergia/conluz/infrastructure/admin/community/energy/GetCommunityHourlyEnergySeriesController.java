package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityHourlyEnergySeriesService;
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
        value = "/api/v1/communities/{communityId}/energy-series/hourly",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetCommunityHourlyEnergySeriesController {

    private final GetCommunityHourlyEnergySeriesService service;

    public GetCommunityHourlyEnergySeriesController(GetCommunityHourlyEnergySeriesService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the energy of a community, added up per hour.",
            description = """
                    For each hour of the requested period, the self-consumption and surplus of every
                    **enabled** supply of the community with data in it, whatever the membership status
                    of its owner, added up. Only community totals are reported: no figure, identifier or
                    code of a single supply or member. Each energy figure sums the unrounded figures and
                    is rounded once, to 3 decimals, on the result.

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    **Maximum length:** on this operation the limit of rule 5 is **31 calendar days**
                    instead of 24 calendar months: `endDate` must be no later than 31 calendar days after
                    `startDate`, counted in the period's time zone on the bounds as requested
                    (`ENERGY_METRICS_PERIOD_TOO_LONG`, with `params` `{"amount": "31", "unit": "DAYS"}`).

                    **Every hour is emitted:**
                    A bucket is returned for every whole hour that starts inside the period, in
                    chronological order, whether or not any supply has data that hour: 24 for a whole
                    local day of the community's time zone, 23 on the day the clocks go forward and 25
                    on the day they go back. `startsAt` is the instant the hour starts at, with the
                    offset in force in the community's time zone, and is unique within the series;
                    `date` and `time` are the local date and time the hour starts at, which two
                    consecutive hours share on the day the clocks go back.

                    **Completeness:**
                    `supplyCount` is the number of enabled supplies of the community, the same in every
                    bucket, and `suppliesWithData` how many of them have data in the hour. A hour with
                    `suppliesWithData` below `supplyCount` is incomplete.

                    **Gaps and measured zeros:**
                    A supply has data in an hour when its record of that hour carries
                    self-consumed energy, zero included; surplus alone does not count. An hour in which
                    no supply has data has `null` energy figures and a `suppliesWithData` of 0, never
                    zeros; an hour whose supplies with data carry no energy is a measured zero, `0`.

                    Readable by any active member of the community, whatever their role. A platform
                    admin who is not a member is answered 403, and every other caller who may not read
                    it 404.
                    """,
            tags = ApiTag.COMMUNITIES,
            operationId = "getCommunityHourlyEnergySeries",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Hourly energy series retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadCommunityProduction(#communityId)")
    public List<CommunityHourlyEnergyBucketResponse> getCommunityHourlyEnergySeries(
            @PathVariable("communityId") UUID communityId,
            @Parameter(description = "Start of the period, inclusive.")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of the period, exclusive.")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return service.getHourlySeries(communityId, startDate, endDate)
                .stream()
                .map(CommunityHourlyEnergyBucketResponse::new)
                .toList();
    }
}
