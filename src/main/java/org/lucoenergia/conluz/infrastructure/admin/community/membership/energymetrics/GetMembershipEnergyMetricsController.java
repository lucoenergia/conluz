package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.GetMembershipEnergyMetricsService;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetrics;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
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
        value = "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetMembershipEnergyMetricsController {

    private final GetMembershipEnergyMetricsService service;

    public GetMembershipEnergyMetricsController(GetMembershipEnergyMetricsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the energy metrics of every supply of a membership, added up.",
            description = """
                    Aggregates the consumption data stored for **every** supply the member owns in
                    this community into energy totals and two ratios, on a 0-1 scale. Supplies in the
                    member's other communities are not counted.

                    - `selfSufficiencyRatio`: self-consumed energy divided by total consumed energy.
                    - `selfConsumptionRatio`: self-consumed energy divided by the assigned energy.

                    Every supply is computed over the same period, exactly as the per-supply
                    energy-metrics endpoint computes it, and the results are added up: the energy
                    totals are summed across supplies and each ratio is derived **once** from those
                    sums, never by averaging the per-supply ratios. A ratio whose summed denominator
                    is zero is returned as `null`, never as `0`.

                    **Period:**
                    The period is resolved in exactly one of three ways, and the resolved bounds are
                    always reported in `period`, also when the caller supplied them.

                    - `startDate` and `endDate`: an explicit period, with the same semantics as the
                      per-supply endpoint. They must be supplied together, and a `startDate` after
                      the `endDate` is a bad request. **Both bounds are inclusive**: a single
                      calendar day runs from `00:00` to `23:00` of that day.
                    - `period=LATEST_PUBLISHED_MONTH`: the most recent complete calendar month, in
                      the community's time zone, in which any of the member's supplies has stored
                      assigned production (self-consumed or surplus energy). The distributor
                      publishes a month's assigned production only some days after it ends, so this
                      is not necessarily the previous month: an unpublished previous month is
                      skipped and the search continues backwards through the 24 complete months
                      before the current one. The current month is never a candidate. The period
                      runs from `00:00` of the first day to `23:00` of the last day. Combining it
                      with `startDate` or `endDate` is a bad request.
                    - Neither: from the earliest to the latest record stored for any of the member's
                      supplies.

                    When no period can be resolved -- no assigned production in the search window, no
                    stored record at all, or no supplies to search -- the response is still
                    successful, with null period bounds, zero totals and null ratios.

                    **Coverage:**
                    `coverage.hoursWithData` counts the hourly records found across every supply.
                    `coverage.expectedHours` is the number of hours the period spans **times the
                    number of supplies of the membership**, so a single supply without any record
                    lowers coverage even when every other supply is complete. `supplyCount` and
                    `suppliesWithData` tell one silent supply apart from gaps spread across all of
                    them. Hours without a record are left out of the sums; they are never counted as
                    zero.

                    **Savings:**
                    `savings.amountEur` is an **estimate** of what the self-consumed energy of all
                    the member's supplies was worth, pricing the **energy term before taxes** only,
                    exactly as the per-supply endpoint does, and summed before being rounded.
                    It is `null` only when no period could be resolved; whenever a period exists it
                    is a figure, `0.00` when nothing was priced. An explicit period always resolves,
                    so it never yields `null`. `savings.tariffSource` is `ESTIMATE` when any part of
                    any supply was priced with the estimate, and `savings.estimatedPrice` then carries
                    the estimated price; it is `null` when every supply was priced with its contracted
                    tariff, and when nothing was priced with the estimate.

                    Readable by the member themself and by community admins of this community.
                    Platform admins are **not** granted access on that basis alone and are answered
                    404, as is every other caller who may not read it, so the membership's existence
                    is not disclosed. A membership without supplies is answered successfully, not
                    with 404.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "getMembershipEnergyMetrics",
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
    @PreAuthorize("@communityAccessGuard.canReadMembershipPrivateData(#communityId, #userId)")
    public MembershipEnergyMetricsResponse getMembershipEnergyMetrics(
            @PathVariable("communityId") UUID communityId,
            @PathVariable("userId") UUID userId,
            @Parameter(description = "First instant of an explicit period, inclusive. Requires endDate.")
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "Last instant of an explicit period, inclusive. Requires startDate.")
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @Parameter(description = "A period the server resolves by itself. Cannot be combined with " +
                    "startDate or endDate.")
            @RequestParam(value = "period", required = false) EnergyMetricsReferencePeriod period) {

        MembershipEnergyMetrics metrics = service.getEnergyMetrics(communityId, userId, startDate, endDate, period);

        return new MembershipEnergyMetricsResponse(metrics);
    }
}
