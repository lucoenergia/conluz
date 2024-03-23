package org.lucoenergia.conluz.infrastructure.admin.supply.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.supply.sync.DatadisSuppliesSyncService;
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
@RequestMapping("/api/v1/supplies/datadis/sync")
public class SyncDatadisSuppliesController {

    private final DatadisSuppliesSyncService datadisSuppliesSyncService;

    public SyncDatadisSuppliesController(DatadisSuppliesSyncService datadisSuppliesSyncService) {
        this.datadisSuppliesSyncService = datadisSuppliesSyncService;
    }

    @PostMapping
    @Operation(
            summary = "Synchronize supplies retrieving the information from datadis.es.",
            description = "This endpoint enables users to synchronize all active supplies retrieving the information from datadis.es. Proper authentication, through an authentication token, is required for secure access to this endpoint. A successful request returns an HTTP status code of 200. In cases of errors, the server responds with an appropriate error status code accompanied by a descriptive message to guide users in resolving any issues.",
            tags = ApiTag.SUPPLIES,
            operationId = "syncDatadisSupplies"
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
        datadisSuppliesSyncService.synchronizeSupplies();
    }
}
