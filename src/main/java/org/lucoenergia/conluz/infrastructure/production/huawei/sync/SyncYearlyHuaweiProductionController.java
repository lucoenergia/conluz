package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production/huawei/sync/yearly")
public class SyncYearlyHuaweiProductionController {

    private final HuaweiProductionYearlyAggregationService aggregationService;

    public SyncYearlyHuaweiProductionController(HuaweiProductionYearlyAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @PostMapping
    @Operation(
            summary = "Aggregate monthly Huawei production data into yearly totals",
            description = """
                    This endpoint enables admins to aggregate monthly production data into yearly totals.

                    The request body must contain:
                    - **year** (required, integer): The year for which to aggregate data
                    - **plantCode** (optional, string): The plant code to aggregate. If not provided, all plants will be aggregated.

                    **Behavior:**
                    - If plantCode is provided: Aggregates only that specific plant
                    - If plantCode is not provided or is empty: Aggregates all plants

                    **Note:** This aggregation requires that monthly aggregations have already been performed
                    for the specified year.

                    Proper authentication, through an authentication token, is required for secure access to this endpoint.
                    **Required Role: ADMIN**

                    A successful request returns an HTTP status code of 200.
                    """,
            tags = ApiTag.PRODUCTION,
            operationId = "syncYearlyHuaweiProduction",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
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
    @PreAuthorize("hasRole('ADMIN')")
    public void syncYearlyHuaweiProduction(@Valid @RequestBody SyncYearlyHuaweiProductionBody body) {

        if (body.getPlantCode() != null && !body.getPlantCode().isBlank()) {
            aggregationService.aggregateYearlyProductions(body.getPlantCode(), body.getYear());
        } else {
            aggregationService.aggregateYearlyProductions(body.getYear());
        }
    }
}
