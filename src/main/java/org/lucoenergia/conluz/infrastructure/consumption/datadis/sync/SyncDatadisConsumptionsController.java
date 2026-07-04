package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/consumption/datadis/sync")
public class SyncDatadisConsumptionsController {

    private final DatadisSyncService datadisSyncService;

    public SyncDatadisConsumptionsController(DatadisSyncService datadisSyncService) {
        this.datadisSyncService = datadisSyncService;
    }

    @PostMapping
    @Operation(
            summary = "Synchronize the consumptions for a specific year from datadis, optionally filtering by supply code.",
            description = """
                    This endpoint enables users to synchronize consumption data from datadis for a specific year.

                    The request body must contain:
                    - **year** (required, integer): The year for which to synchronize consumption data
                    - **supplyCode** (optional, string): The supply code (CUPS) to synchronize. If not provided, all active supplies will be synchronized.

                    The synchronization will retrieve data from January 1st to December 31st of the specified year.

                    **Behavior:**
                    - If supplyCode is provided: Synchronizes only that specific supply
                    - If supplyCode is not provided or is empty: Synchronizes all active supplies

                    The community is taken from the path; the configured Datadis credentials of that
                    community are used and only that community's supplies are synchronized.

                    Proper authentication, through an authentication token, is required for secure access to this endpoint.
                    **Required: Community Admin of the community. Returns 404 if the community does not exist or the
                    caller is not a member of it, or 403 if the caller is a member but not one of its admins.**

                    A successful request returns an HTTP status code of 200.

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
        datadisSyncService.synchronize(communityId, body.getStartDate(), body.getEndDate(), body.getSupplyCode());
    }
}
