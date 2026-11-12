package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.GetMembershipMonthlyConsumptionService;
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
        value = "/api/v1/communities/{communityId}/memberships/{userId}/consumption/monthly",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetMembershipMonthlyConsumptionController {

    private final GetMembershipMonthlyConsumptionService service;

    public GetMembershipMonthlyConsumptionController(GetMembershipMonthlyConsumptionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the monthly consumption of every supply of a membership, added up per month.",
            description = """
                    For each month of the requested period, the energy and the estimated savings of
                    the supplies the member owns in this community that have data that month, added
                    up. Each energy figure sums the unrounded per-supply monthly totals and is
                    rounded once, to 3 decimals,
                    on the result; the savings are likewise summed unrounded and rounded once, to
                    cents. A figure can therefore differ from adding up the rounded figures of the
                    per-supply monthly series. Supplies in the member's other communities are not
                    counted.

                    **Source:**
                    Each supply's month is summed from its hourly records when the series is read,
                    so it is as fresh as the energy metrics.

                    **Supplies with data:**
                    Datadis publishes a month's self-consumption around the 10th of the following
                    month; until then the supply's records carry consumption but no
                    self-consumption. A supply has data in a month only when its records carrying
                    self-consumption cover at least 90 % of the month's hours inside the period. A
                    supply below that does not contribute to the month at all, so an unpublished
                    month is never summed as zero self-consumption.

                    **Every month is emitted:**
                    A bucket is returned for every month in range, in chronological order, whether or
                    not any supply has data in it, so the series has a regular time axis. This
                    differs from the per-supply monthly series, which omits months without data.

                    **Time zone and month selection:**
                    Months are local calendar months of the community's time zone, not UTC ones.
                    `startDate` and `endDate` are both required, and a month is in range if and only
                    if its local midnight on day 1 falls inside them, the rule the per-supply
                    monthly series selects its months by. A month the end bound falls inside counts
                    only its hours before that bound, energy and savings alike. Pass the bounds with
                    the zone's offset: a bound expressed in UTC can select one month too few or too
                    many.

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    **Completeness:**
                    `supplyCount` is the number of supplies the member **currently** owns in the
                    community, and `suppliesWithData` how many of them have data that month, as
                    defined above. A month with `suppliesWithData` below `supplyCount` is
                    incomplete. The model records no date a supply joined its community, so the
                    count is the same for every month, and the months before a supply started
                    reporting show as incomplete. `suppliesWithData` is measured month by month; the
                    aggregated energy metrics measure theirs over the whole period, so the two are
                    not comparable.

                    **Savings:**
                    `savingsEur` is an **estimate** of what the month's self-consumed energy was
                    worth, pricing the **energy term before taxes** only, exactly as the per-supply
                    series does. It is `null` when no supply has data that month, and only then: a
                    month without data is a different statement from a month that saved nothing,
                    which reports `0.00` -- as does a month whose published self-consumption is zero,
                    a measured zero. This differs from the per-supply series,
                    which reports `0.00` for every bucket it emits. `tariffSource` says where the
                    prices came from -- `ESTIMATE` or `REAL_TARIFF` -- and a single estimated supply
                    makes the whole month an estimate; it is `null` exactly when `savingsEur` is.

                    Readable by the member themself and by community admins of this community.
                    Platform admins are **not** granted access on that basis alone and are answered
                    404, as is every other caller who may not read it, so the membership's existence
                    is not disclosed. A membership without supplies is answered successfully, not
                    with 404: every month in range, with zero totals, null savings and a
                    `supplyCount` of 0.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "getMembershipMonthlyConsumption",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Monthly consumption retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadMembershipPrivateData(#communityId, #userId)")
    public List<MembershipMonthlyConsumptionBucketResponse> getMembershipMonthlyConsumption(
            @PathVariable("communityId") UUID communityId,
            @PathVariable("userId") UUID userId,
            @Parameter(description = "Start of the period, inclusive.")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of the period, exclusive.")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return service.getMonthlySeries(communityId, userId, startDate, endDate)
                .stream()
                .map(MembershipMonthlyConsumptionBucketResponse::new)
                .toList();
    }
}
