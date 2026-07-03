package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
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
@RequestMapping("/api/v1/communities/{communityId}/consumption/datadis/sync/monthly")
public class SyncMonthlyDatadisConsumptionsController {

    private final DatadisMonthlyAggregationService aggregationService;

    public SyncMonthlyDatadisConsumptionsController(DatadisMonthlyAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @PostMapping
    @Operation(
            summary = "Aggregate hourly Datadis consumption data into monthly totals",
            description = """
                    This endpoint enables admins to aggregate hourly consumption data into monthly totals.

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
            tags = ApiTag.CONSUMPTION,
            operationId = "syncMonthlyDatadisConsumptions",
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
    public void syncMonthlyDatadisConsumptions(@PathVariable UUID communityId,
                                               @Valid @RequestBody SyncMonthlyDatadisConsumptionsBody body) {
        aggregationService.syncMonthlyConsumptions(communityId, body.getSupplyCode(), body.getMonth(), body.getYear());
    }
}
