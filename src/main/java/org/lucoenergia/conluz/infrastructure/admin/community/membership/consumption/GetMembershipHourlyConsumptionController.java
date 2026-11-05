package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.GetMembershipHourlyConsumptionService;
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
        value = "/api/v1/communities/{communityId}/memberships/{userId}/consumption/hourly",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetMembershipHourlyConsumptionController {

    private final GetMembershipHourlyConsumptionService service;

    public GetMembershipHourlyConsumptionController(GetMembershipHourlyConsumptionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the hourly consumption of every supply of a membership, added up per hour.",
            description = """
                    For each hour of the requested period, the energy and the estimated savings of
                    **every** supply the member owns in this community that stored a record that hour,
                    added up. Each energy figure sums the unrounded per-supply hourly figures and is
                    rounded once, to 3 decimals, on the result; the savings are likewise summed
                    unrounded and rounded once, to cents. A figure can therefore differ from adding
                    up the rounded figures of the per-supply hourly series. Supplies in the member's
                    other communities are not counted.

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    **Maximum length:** on this operation the limit of rule 5 is **31 calendar days**
                    instead of 24 calendar months: `endDate` must be no later than 31 calendar days after
                    `startDate`, counted in the period's time zone on the bounds as requested
                    (`ENERGY_METRICS_PERIOD_TOO_LONG`).

                    **Every hour is emitted:**
                    A bucket is returned for every whole hour that starts inside the period, in
                    chronological order, whether or not any supply stored a record that hour, so the
                    series has a regular time axis: 24 for a whole local day of the community's time
                    zone, 23 on the day the clocks go forward and 25 on the day they go back. A period
                    starting in the middle of an hour starts with the next whole hour; an `endDate` in
                    the middle of an hour leaves a partial last hour, counted only up to the end.

                    **`startsAt`:** the instant the hour starts at, with the offset in force in the
                    community's time zone at that instant -- for example `2026-10-25T02:00:00+02:00`
                    and then `2026-10-25T02:00:00+01:00` for the two hours the clocks repeat in
                    Europe/Madrid. It is unique within the series and is the key to identify an hour by.
                    `date` and `time` are the local date and time the hour starts at; on the day the
                    clocks go back, two consecutive hours carry the same ones.

                    **Completeness:**
                    `supplyCount` is the number of supplies the member **currently** owns in the
                    community, and `suppliesWithData` how many of them stored a record that hour. An
                    hour with `suppliesWithData` below `supplyCount` is incomplete. The model records
                    no date a supply joined its community, so the count is the same for every hour,
                    and the hours before a supply started reporting show as incomplete. The aggregated
                    energy metrics measure their `suppliesWithData` over the whole period, so the two
                    are not comparable.

                    **Gaps and measured zeros:**
                    The energy figures add up only the supplies that stored a record that hour; an
                    hour in which no supply stored one reports zero energy. `savingsEur` is an
                    **estimate** of what the hour's self-consumed energy was worth, pricing the
                    **energy term before taxes** only, exactly as the per-supply series does. It is
                    `null` when no supply stored a record that hour (`suppliesWithData` is 0), and
                    only then: an hour with nothing stored is a different statement from an hour that
                    saved nothing, which reports `0.00` -- as does an hour whose stored records carry
                    no self-consumption, a measured zero. This differs from the per-supply series,
                    which reports `0.00` for every bucket it emits. `tariffSource` says where the
                    prices came from -- `ESTIMATE` or `REAL_TARIFF` -- and a single estimated supply
                    makes the whole hour an estimate; it is `null` exactly when `savingsEur` is.

                    Readable by the member themself and by community admins of this community.
                    Platform admins are **not** granted access on that basis alone and are answered
                    404, as is every other caller who may not read it, so the membership's existence
                    is not disclosed. A membership without supplies is answered successfully, not
                    with 404: every hour in range, with zero totals, null savings and a
                    `supplyCount` of 0.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "getMembershipHourlyConsumption",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Hourly consumption retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadMembershipPrivateData(#communityId, #userId)")
    public List<MembershipHourlyConsumptionBucketResponse> getMembershipHourlyConsumption(
            @PathVariable("communityId") UUID communityId,
            @PathVariable("userId") UUID userId,
            @Parameter(description = "Start of the period, inclusive.")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of the period, exclusive.")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return service.getHourlySeries(communityId, userId, startDate, endDate)
                .stream()
                .map(MembershipHourlyConsumptionBucketResponse::new)
                .toList();
    }
}
