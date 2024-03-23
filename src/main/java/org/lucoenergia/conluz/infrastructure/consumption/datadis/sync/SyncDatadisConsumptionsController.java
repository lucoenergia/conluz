package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumptionSyncService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/consumption/datadis/sync")
public class SyncDatadisConsumptionsController {

    private final DatadisConsumptionSyncService datadisConsumptionSyncService;

    public SyncDatadisConsumptionsController(DatadisConsumptionSyncService datadisConsumptionSyncService) {
        this.datadisConsumptionSyncService = datadisConsumptionSyncService;
    }

    @PostMapping
    @Operation(
            summary = "Synchronize the consumptions of all active supplies retrieving the information from datadis.es.",
            description = "This endpoint enables users to synchronize the consumptions of all active supplies retrieving the information from datadis.es. Proper authentication, through an authentication token, is required for secure access to this endpoint. A successful request returns an HTTP status code of 200. In cases of errors, the server responds with an appropriate error status code accompanied by a descriptive message to guide users in resolving any issues.",
            tags = ApiTag.CONSUMPTION,
            operationId = "syncDatadisConsumptions"
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
    @InternalServerErrorResponse
    public void syncDatadisConsumptions() {
        datadisConsumptionSyncService.synchronizeConsumptions();
    }
}
