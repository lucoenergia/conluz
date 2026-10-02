package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.GetMembershipMonthlyConsumptionService;
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
                    **every** supply the member owns in this community, added up: each figure is the
                    sum of what the per-supply monthly series reports for that supply and month.
                    Supplies in the member's other communities are not counted.

                    **Every month is emitted:**
                    A bucket is returned for every month in range, in chronological order, whether or
                    not any supply stored a record in it, so the series has a regular time axis. This
                    differs from the per-supply monthly series, which omits months without a stored
                    record.

                    **Time zone and month selection:**
                    Months are local calendar months of the community's time zone, not UTC ones.
                    `startDate` and `endDate` are both required and both inclusive, and a month is in
                    range if and only if its local midnight on day 1 falls inside them -- the rule the
                    per-supply monthly series selects its pre-aggregated points by. Once in range a
                    month always carries its whole energy and its whole savings: bounds falling
                    mid-month never trim it. Pass the bounds with the zone's offset: a bound expressed
                    in UTC can select one month too few or too many.

                    **Completeness:**
                    `supplyCount` is the number of supplies the member **currently** owns in the
                    community, and `suppliesWithData` how many of them stored a monthly record that
                    month. A month with `suppliesWithData` below `supplyCount` is incomplete. The
                    model records no date a supply joined its community, so the count is the same
                    for every month, and the months before a supply started reporting show as
                    incomplete. `suppliesWithData` is measured from stored monthly records, month by
                    month; the aggregated energy metrics measure theirs from hourly records over the
                    whole period, so the two are not comparable.

                    **Savings:**
                    `savingsEur` is an **estimate** of what the month's self-consumed energy was
                    worth, pricing the **energy term before taxes** only, exactly as the per-supply
                    series does. It is `null` when no supply stored a record that month, and only
                    then: a month with nothing stored is a different statement from a month that
                    saved nothing, which reports `0.00` -- as does a month whose stored records carry
                    no self-consumption, a measured zero. This differs from the per-supply series,
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
            @Parameter(description = "First instant of the period, inclusive.")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "Last instant of the period, inclusive.")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return service.getMonthlySeries(communityId, userId, startDate, endDate)
                .stream()
                .map(MembershipMonthlyConsumptionBucketResponse::new)
                .toList();
    }
}
