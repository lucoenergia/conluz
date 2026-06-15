package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.sync.DatadisConsumptionSyncService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisDisabledException;
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

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/consumption/datadis/sync")
public class SyncDatadisConsumptionsController {

    private final DatadisConsumptionSyncService datadisConsumptionSyncService;
    private final GetDatadisConfigRepository getDatadisConfigRepository;
    private final CommunityAccessGuard communityAccessGuard;

    public SyncDatadisConsumptionsController(DatadisConsumptionSyncService datadisConsumptionSyncService,
                                             GetDatadisConfigRepository getDatadisConfigRepository,
                                             CommunityAccessGuard communityAccessGuard) {
        this.datadisConsumptionSyncService = datadisConsumptionSyncService;
        this.getDatadisConfigRepository = getDatadisConfigRepository;
        this.communityAccessGuard = communityAccessGuard;
    }

    @PostMapping
    @Operation(
            summary = "Synchronize the consumptions for a specific year from datadis.es, optionally filtering by supply code.",
            description = """
                    This endpoint enables users to synchronize consumption data from datadis.es for a specific year.

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
                    **Required: Community Admin of the community. Returns 404 if the community does not exist or cannot be managed.**

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
    @PreAuthorize("isAuthenticated()")
    public void syncDatadisConsumptions(@PathVariable UUID communityId,
                                        @Valid @RequestBody SyncDatadisConsumptionsBody body) {
        if (!communityAccessGuard.canManageCommunity(communityId)) {
            throw new CommunityNotFoundException(communityId);
        }

        Optional<DatadisConfig> config = getDatadisConfigRepository.findByCommunityId(communityId);
        if (config.isEmpty() || !Boolean.TRUE.equals(config.get().getEnabled())) {
            throw new DatadisDisabledException();
        }

        if (body.getSupplyCode() != null && !body.getSupplyCode().isBlank()) {
            datadisConsumptionSyncService.synchronizeConsumptions(
                    communityId,
                    body.getStartDate(),
                    body.getEndDate(),
                    SupplyCode.of(body.getSupplyCode())
            );
        } else {
            datadisConsumptionSyncService.synchronizeConsumptions(communityId, body.getStartDate(), body.getEndDate());
        }
    }
}
