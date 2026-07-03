package org.lucoenergia.conluz.infrastructure.production.datadis.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
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
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/production/datadis/sync/monthly")
public class SyncMonthlyDatadisProductionController {

    private final DatadisProductionMonthlyAggregationService aggregationService;
    private final GetDatadisConfigRepository getDatadisConfigRepository;

    public SyncMonthlyDatadisProductionController(DatadisProductionMonthlyAggregationService aggregationService,
                                                  GetDatadisConfigRepository getDatadisConfigRepository) {
        this.aggregationService = aggregationService;
        this.getDatadisConfigRepository = getDatadisConfigRepository;
    }

    @PostMapping
    @Operation(
            summary = "Aggregate hourly Datadis production data into monthly totals",
            description = """
                    This endpoint enables admins to aggregate hourly production data into monthly totals.

                    The request body must contain:
                    - **year** (required, integer): The year for which to aggregate data
                    - **month** (optional, integer 1-12): The month to aggregate. If not provided, all months of the year will be aggregated.
                    - **supplyCode** (optional, string): The supply code (CUPS) to aggregate. If not provided, all active supplies will be aggregated.

                    **Behavior:**
                    - If both month and supplyCode are provided: Aggregates only that specific supply for that month
                    - If only month is provided: Aggregates all supplies for that specific month
                    - If only supplyCode is provided: Aggregates that supply for all months of the year
                    - If neither month nor supplyCode is provided: Aggregates all supplies for all months of the year

                    The community is taken from the path and only that community's supplies are aggregated.

                    Proper authentication, through an authentication token, is required for secure access to this endpoint.
                    **Required: Community Admin of the community. Returns 404 if the community does not exist or the
                    caller is not a member of it, or 403 if the caller is a member but not one of its admins.**

                    A successful request returns an HTTP status code of 200.
                    """,
            tags = ApiTag.PRODUCTION,
            operationId = "syncMonthlyDatadisProduction",
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
    @PreAuthorize("@communityAccessGuard.canManageCommunity(#communityId)")
    public void syncMonthlyDatadisProduction(@PathVariable UUID communityId,
                                             @Valid @RequestBody SyncMonthlyDatadisProductionBody body) {
        Optional<DatadisConfig> config = getDatadisConfigRepository.findByCommunityId(communityId);
        if (config.isEmpty() || !Boolean.TRUE.equals(config.get().getEnabled())) {
            throw new DatadisDisabledException();
        }

        if (body.getSupplyCode() != null && !body.getSupplyCode().isBlank()) {
            if (body.getMonth() != null) {
                // Specific supply, specific month
                aggregationService.aggregateMonthlyProductions(
                        communityId,
                        SupplyCode.of(body.getSupplyCode()),
                        body.getMonthEnum(),
                        body.getYear()
                );
            } else {
                // Specific supply, all months of year
                for (Month month : Month.values()) {
                    aggregationService.aggregateMonthlyProductions(
                            communityId,
                            SupplyCode.of(body.getSupplyCode()),
                            month,
                            body.getYear()
                    );
                }
            }
        } else {
            if (body.getMonth() != null) {
                // All community supplies, specific month
                aggregationService.aggregateMonthlyProductions(communityId, body.getMonthEnum(), body.getYear());
            } else {
                // All community supplies, all months
                aggregationService.aggregateMonthlyProductions(communityId, body.getYear());
            }
        }
    }
}
