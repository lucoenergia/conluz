package org.lucoenergia.conluz.infrastructure.datadis.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindows;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/consumption/datadis/sync")
public class SyncDatadisConsumptionsController {

    private final DatadisSyncService datadisSyncService;
    private final Clock clock;

    public SyncDatadisConsumptionsController(DatadisSyncService datadisSyncService, Clock clock) {
        this.datadisSyncService = datadisSyncService;
        this.clock = clock;
    }

    @PostMapping
    @Operation(
            summary = "Synchronize the consumptions of the last months from datadis, optionally filtering by supply code.",
            description = """
                    This endpoint enables users to synchronize consumption data from datadis for the current month
                    and the months before it.

                    The request body must contain:
                    - **monthsBack** (required, integer): How many months before the current one the
                      synchronization starts. 0 synchronizes the current month only.
                    - **supplyCode** (optional, string): The supply code (CUPS) to synchronize. If not provided, all active supplies will be synchronized.

                    The synchronization retrieves data from the first day of the month `monthsBack` months before
                    today through today: with `monthsBack` 3 on 26 November, from 1 August through 26 November.
                    """
                    + "Datadis serves at most " + DatadisSyncWindows.MAX_LOOKBACK_MONTHS + " months back when "
                    + "queried by month, so `monthsBack` goes from 0 to " + DatadisSyncWindows.MAX_LOOKBACK_MONTHS
                    + ". Any other value, a missing one or the former `year` field is answered with `400`, and "
                    + "Datadis is not called.\n"
                    + """

                    **Behavior:**
                    - If supplyCode is provided: Synchronizes only that specific supply
                    - If supplyCode is not provided or is empty: Synchronizes all active supplies

                    The community is taken from the path; the configured Datadis credentials of that
                    community are used and only that community's supplies are synchronized.

                    Proper authentication, through an authentication token, is required for secure access to this endpoint.
                    **Required: Community Admin of the community. Returns 404 if the community does not exist or the
                    caller is not a member of it, or 403 if the caller is a member but not one of its admins.**

                    A successful request returns an HTTP status code of 200.

                    The server responds with `409` when the community has no Datadis configuration, or it is
                    disabled; Datadis is not called.

                    In cases of errors, the server responds with an appropriate error status code accompanied by a
                    descriptive message to guide users in resolving any issues.
                    """,
            tags = ApiTag.CONSUMPTION,
            operationId = "syncDatadisConsumptions",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Synchronization executed successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The community has no Datadis configuration, or it is disabled.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-11-09T10:10:25.534035352+01:00",
                                               "status": 409,
                                               "message": "Datadis integration is disabled. Enable it via POST /api/v1/communities/{communityId}/config/datadis/enable before triggering this operation.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013"
                                            }
                                            """
                            )
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunity(#communityId)")
    public void syncDatadisConsumptions(@PathVariable UUID communityId,
                                        @Valid @RequestBody SyncDatadisConsumptionsBody body) {
        LocalDate endDate = body.getEndDate(clock);
        datadisSyncService.synchronize(communityId, body.getStartDate(endDate), endDate, body.getSupplyCode());
    }
}
