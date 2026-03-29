package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
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

import java.time.Month;

@RestController
@RequestMapping("/api/v1/production/huawei/sync/monthly")
public class SyncMonthlyHuaweiProductionController {

    private final HuaweiProductionMonthlyAggregationService aggregationService;

    public SyncMonthlyHuaweiProductionController(HuaweiProductionMonthlyAggregationService aggregationService) {
        this.aggregationService = aggregationService;
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

                    Proper authentication, through an authentication token, is required for secure access to this endpoint.
                    **Required Role: ADMIN**

                    A successful request returns an HTTP status code of 200.
                    """,
            tags = ApiTag.PRODUCTION,
            operationId = "syncMonthlyHuaweiProduction",
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
    public void syncMonthlyHuaweiProduction(@Valid @RequestBody SyncMonthlyHuaweiProductionBody body) {

        if (body.getPlantCode() != null && !body.getPlantCode().isBlank()) {
            if (body.getMonth() != null) {
                // Specific plant, specific month
                aggregationService.aggregateMonthlyProductions(
                        body.getPlantCode(),
                        body.getMonthEnum(),
                        body.getYear()
                );
            } else {
                // Specific plant, all months of year
                for (Month month : Month.values()) {
                    aggregationService.aggregateMonthlyProductions(
                            body.getPlantCode(),
                            month,
                            body.getYear()
                    );
                }
            }
        } else {
            if (body.getMonth() != null) {
                // All plants, specific month
                aggregationService.aggregateMonthlyProductions(body.getMonthEnum(), body.getYear());
            } else {
                // All plants, all months
                aggregationService.aggregateMonthlyProductions(body.getYear());
            }
        }
    }
}
