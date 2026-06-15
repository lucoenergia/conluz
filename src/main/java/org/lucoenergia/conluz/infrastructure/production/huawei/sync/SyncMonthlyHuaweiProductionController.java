package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiDisabledException;
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

import java.time.Month;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/production/huawei/sync/monthly")
public class SyncMonthlyHuaweiProductionController {

    private final HuaweiProductionMonthlyAggregationService aggregationService;
    private final GetHuaweiConfigRepository getHuaweiConfigRepository;
    private final CommunityAccessGuard communityAccessGuard;

    public SyncMonthlyHuaweiProductionController(HuaweiProductionMonthlyAggregationService aggregationService,
                                                 GetHuaweiConfigRepository getHuaweiConfigRepository,
                                                 CommunityAccessGuard communityAccessGuard) {
        this.aggregationService = aggregationService;
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
        this.communityAccessGuard = communityAccessGuard;
    }

    @PostMapping
    @Operation(
            summary = "Aggregate hourly Huawei production data into monthly totals",
            description = """
                    This endpoint enables admins to aggregate hourly production data into monthly totals.

                    The request body must contain:
                    - **year** (required, integer): The year for which to aggregate data
                    - **month** (optional, integer 1-12): The month to aggregate. If not provided, all months of the year will be aggregated.
                    - **plantCode** (optional, string): The plant code to aggregate. If not provided, all plants will be aggregated.

                    **Behavior:**
                    - If both month and plantCode are provided: Aggregates only that specific plant for that month
                    - If only month is provided: Aggregates all plants for that specific month
                    - If only plantCode is provided: Aggregates that plant for all months of the year
                    - If neither month nor plantCode is provided: Aggregates all plants for all months of the year

                    The community is taken from the path and only that community's plants are aggregated.

                    Proper authentication, through an authentication token, is required for secure access to this endpoint.
                    **Required: Community Admin of the community. Returns 404 if the community does not exist or cannot be managed.**

                    A successful request returns an HTTP status code of 200.
                    """,
            tags = ApiTag.PRODUCTION,
            operationId = "syncMonthlyHuaweiProduction",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Synchronization executed successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("isAuthenticated()")
    public void syncMonthlyHuaweiProduction(@PathVariable UUID communityId,
                                            @Valid @RequestBody SyncMonthlyHuaweiProductionBody body) {
        if (!communityAccessGuard.canManageCommunity(communityId)) {
            throw new CommunityNotFoundException(communityId);
        }

        if (getHuaweiConfigRepository.getEnabledHuaweiConfigs().isEmpty()) {
            throw new HuaweiDisabledException();
        }

        if (body.getPlantCode() != null && !body.getPlantCode().isBlank()) {
            if (body.getMonth() != null) {
                // Specific plant, specific month
                aggregationService.aggregateMonthlyProductions(
                        communityId,
                        body.getPlantCode(),
                        body.getMonthEnum(),
                        body.getYear()
                );
            } else {
                // Specific plant, all months of year
                for (Month month : Month.values()) {
                    aggregationService.aggregateMonthlyProductions(
                            communityId,
                            body.getPlantCode(),
                            month,
                            body.getYear()
                    );
                }
            }
        } else {
            if (body.getMonth() != null) {
                // All community plants, specific month
                aggregationService.aggregateMonthlyProductions(communityId, body.getMonthEnum(), body.getYear());
            } else {
                // All community plants, all months
                aggregationService.aggregateMonthlyProductions(communityId, body.getYear());
            }
        }
    }
}
