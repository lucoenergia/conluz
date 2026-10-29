package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.GetMembershipDailyConsumptionService;
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
        value = "/api/v1/communities/{communityId}/memberships/{userId}/consumption/daily",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetMembershipDailyConsumptionController {

    private final GetMembershipDailyConsumptionService service;

    public GetMembershipDailyConsumptionController(GetMembershipDailyConsumptionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the daily consumption of every supply of a membership, added up per day.",
            description = """
                    For each day of the requested period, the energy and the estimated savings of
                    **every** supply the member owns in this community that stored a record that day,
                    added up. Each energy figure sums the unrounded per-supply daily totals and is
                    rounded once, to 3 decimals, on the result; the savings are likewise summed
                    unrounded and rounded once, to cents. A figure can therefore differ from adding
                    up the rounded figures of the per-supply daily series. Supplies in the member's
                    other communities are not counted.

                    **Source and freshness:**
                    Each supply's day is summed from its hourly records when it is read, exactly as
                    the per-supply daily series sums it, so a day reflects the hourly records stored
                    at the time of the request.

                    **Every day is emitted:**
                    A bucket is returned for every local calendar day of the community's time zone
                    that overlaps the period, in chronological order, whether or not any supply
                    stored a record that day, so the series has a regular time axis. A day runs
                    from its local midnight to the next one, so the days on which daylight saving
                    time starts and ends cover 23 and 25 hours. The first and the last day are cut
                    to the period: a period starting at 12:00 counts only that day's hours from
                    12:00, in energy and in savings, and a period clipped to the current hour ends
                    with today's hours up to then. `date` is the local day and `time` always
                    `00:00`, also on a cut first day.

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    **Completeness:**
                    `supplyCount` is the number of supplies the member **currently** owns in the
                    community, and `suppliesWithData` how many of them stored at least one hourly
                    record that day, within the period. A day with `suppliesWithData` below
                    `supplyCount` is incomplete. The model records no date a supply joined its
                    community, so the count is the same for every day, and the days before a supply
                    started reporting show as incomplete. The aggregated energy metrics measure
                    their `suppliesWithData` over the whole period, so the two are not comparable.

                    **Gaps and measured zeros:**
                    The energy figures add up only the supplies that stored a record that day; a
                    day on which no supply stored one reports zero energy. `savingsEur` is an
                    **estimate** of what the day's self-consumed energy was worth, pricing the
                    **energy term before taxes** only, exactly as the per-supply series does. It is
                    `null` when no supply stored a record that day (`suppliesWithData` is 0), and
                    only then: a day with nothing stored is a different statement from a day that
                    saved nothing, which reports `0.00` -- as does a day whose stored records carry
                    no self-consumption, a measured zero. This differs from the per-supply series,
                    which reports `0.00` for every bucket it emits. `tariffSource` says where the
                    prices came from -- `ESTIMATE` or `REAL_TARIFF` -- and a single estimated supply
                    makes the whole day an estimate; it is `null` exactly when `savingsEur` is.

                    Readable by the member themself and by community admins of this community.
                    Platform admins are **not** granted access on that basis alone and are answered
                    404, as is every other caller who may not read it, so the membership's existence
                    is not disclosed. A membership without supplies is answered successfully, not
                    with 404: every day in range, with zero totals, null savings and a
                    `supplyCount` of 0.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "getMembershipDailyConsumption",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Daily consumption retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadMembershipPrivateData(#communityId, #userId)")
    public List<MembershipDailyConsumptionBucketResponse> getMembershipDailyConsumption(
            @PathVariable("communityId") UUID communityId,
            @PathVariable("userId") UUID userId,
            @Parameter(description = "Start of the period, inclusive.")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of the period, exclusive.")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return service.getDailySeries(communityId, userId, startDate, endDate)
                .stream()
                .map(MembershipDailyConsumptionBucketResponse::new)
                .toList();
    }
}
