package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationService;
import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConfigRepository;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/consumption/datadis/sync/yearly")
public class SyncYearlyDatadisConsumptionsController {

    private final DatadisYearlyAggregationService aggregationService;
    private final GetDatadisConfigRepository getDatadisConfigRepository;

    public SyncYearlyDatadisConsumptionsController(DatadisYearlyAggregationService aggregationService,
                                                   GetDatadisConfigRepository getDatadisConfigRepository) {
        this.aggregationService = aggregationService;
        this.getDatadisConfigRepository = getDatadisConfigRepository;
    }

    @PostMapping
    @Operation(
            summary = "Sync monthly Datadis consumption data into yearly totals",
            description = """
                    This endpoint enables admins to sync monthly consumption data into yearly totals.

                    The request body must contain:
                    - **year** (required, integer): The year for which to aggregate data
                    - **supplyCode** (optional, string): The supply code (CUPS) to aggregate. If not provided, all active supplies will be aggregated.

                    **Behavior:**
                    - If supplyCode is provided: Aggregates only that specific supply
                    - If supplyCode is not provided or is empty: Aggregates all active supplies

                    **Note:** This aggregation requires that monthly aggregations have already been performed
                    for the specified year.

                    Proper authentication, through an authentication token, is required for secure access to this endpoint.
                    **Required Role: ADMIN**

                    A successful request returns an HTTP status code of 200.
                    """,
            tags = ApiTag.CONSUMPTION,
            operationId = "syncYearlyDatadisConsumptions",
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
    public void syncYearlyDatadisConsumptions(@Valid @RequestBody SyncYearlyDatadisConsumptionsBody body) {

        Optional<DatadisConfig> config = getDatadisConfigRepository.getDatadisConfig();
        if (config.isEmpty() || !Boolean.TRUE.equals(config.get().getEnabled())) {
            throw new DatadisDisabledException();
        }

        if (body.getSupplyCode() != null && !body.getSupplyCode().isBlank()) {
            aggregationService.aggregateYearlyConsumptions(
                    SupplyCode.of(body.getSupplyCode()),
                    body.getYear()
            );
        } else {
            aggregationService.aggregateYearlyConsumptions(body.getYear());
        }
    }
}
