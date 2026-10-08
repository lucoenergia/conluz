package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics.hourlyprofile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile.GetMembershipHourlyProfileService;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile.MembershipHourlyProfile;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics/hourly-profile",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetMembershipHourlyProfileController {

    private final GetMembershipHourlyProfileService service;

    public GetMembershipHourlyProfileController(GetMembershipHourlyProfileService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the average day of every supply of a membership over its latest published month.",
            description = """
                    For each hour of the local day, the average energy consumed and the average
                    energy assigned by **every** supply the member owns in this community, so the
                    member can see at which hours assigned energy goes to waste. Supplies in the
                    member's other communities are not counted.

                    - `averageConsumptionKWh`: grid import plus self-consumed energy.
                    - `averageAssignedProductionKWh`: self-consumed plus surplus energy.

                    **Period:**
                    Always the latest published month, resolved exactly as the aggregated energy
                    metrics resolve `period=LATEST_PUBLISHED_MONTH`: the most recent complete calendar
                    month, in the community's time zone, that the distributor has published for any
                    of the member's supplies. An hourly record is published when it carries
                    self-consumed energy, zero included; surplus alone does not count, since the
                    distributor sends the measured surplus before it publishes the month. A month is
                    published for a supply when at least 90% of the hours it could have published
                    carry it, counted from the later of the month's first hour and the supply's first
                    published record. It **cannot be chosen**: only inside a published month is a
                    stored zero a measured zero rather than a value not published yet. The resolved
                    bounds are reported in `period`. When no month can be resolved -- no published
                    month in the search window, or no supplies to search -- the response is still
                    successful, with null period bounds and 24 buckets without any sample.

                    **Buckets:**
                    Always 24, ordered from hour 0 to hour 23. The hour is **local to the community**,
                    not UTC: each record falls in the bucket of its hour of the day in the community's
                    time zone.

                    **Averages:**
                    Every record of every supply and every day of the month counts as one sample of
                    its hour, and each average **divides by the records found**: never by the days of
                    the month, and never by averaging per-supply averages. Each series has its own
                    sample count, `consumptionSampleCount` and `assignedProductionSampleCount`, and the
                    two can differ, since a record can carry consumption without carrying assigned
                    production. An average is `null` when its hour has no sample of that series, which
                    is never the same as `0`: an hour whose samples are all zero, such as a night hour
                    of assigned production, averages `0`.

                    **Daylight saving:**
                    The counts are sample counts, not day counts, and no hour is corrected. On the
                    October transition day the repeated local hour holds two samples of that day; on
                    the March transition day the skipped local hour holds none. No bucket is ever
                    shifted.

                    **Coverage:**
                    `coverage` is counted exactly as in the aggregated energy metrics:
                    `coverage.expectedHours` is the number of hours the month spans **times the number
                    of supplies of the membership**, so missing records -- including days of a month
                    published piecemeal -- show up as `hoursWithData` below `expectedHours`.
                    `supplyCount` and `suppliesWithData` tell one silent supply apart from gaps spread
                    across all of them.

                    Readable by the member themself and by community admins of this community.
                    Platform admins are **not** granted access on that basis alone and are answered
                    404, as is every other caller who may not read it, so the membership's existence
                    is not disclosed. A membership without supplies is answered successfully, not
                    with 404.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "getMembershipHourlyProfile",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Hourly profile retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadMembershipPrivateData(#communityId, #userId)")
    public MembershipHourlyProfileResponse getMembershipHourlyProfile(
            @PathVariable("communityId") UUID communityId,
            @PathVariable("userId") UUID userId) {

        MembershipHourlyProfile profile = service.getHourlyProfile(communityId, userId);

        return new MembershipHourlyProfileResponse(profile);
    }
}
