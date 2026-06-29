package org.lucoenergia.conluz.infrastructure.admin.supply.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.sync.DatadisSuppliesSyncService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/supplies/datadis/sync")
public class SyncDatadisSuppliesController {

    private final DatadisSuppliesSyncService datadisSuppliesSyncService;

    public SyncDatadisSuppliesController(DatadisSuppliesSyncService datadisSuppliesSyncService) {
        this.datadisSuppliesSyncService = datadisSuppliesSyncService;
    }

    @PostMapping
    @Operation(
            summary = "Synchronize supplies retrieving the information from datadis.",
            description = """
                    This endpoint enables users to synchronize the active supplies of the community identified by the
                    path `communityId`, retrieving the information from datadis.

                    Proper authentication, through an authentication token, is required for secure access to this
                    endpoint.
                    **Required: Community Admin of the community. Returns 404 if the community does not exist or the
                    caller is not a member of it, or 403 if the caller is a member but not one of its admins.**

                    A successful request returns an HTTP status code of 200.
                    
                    In cases of errors, the server responds with an appropriate error status code accompanied by a
                    descriptive message to guide users in resolving any issues.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "syncDatadisSupplies",
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
    public void syncDatadisSupplies(@PathVariable UUID communityId) {
        datadisSuppliesSyncService.synchronizeSupplies(communityId);
    }
}
