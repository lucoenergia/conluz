package org.lucoenergia.conluz.infrastructure.admin.supply.delete;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSupplyService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DeleteSupplyController {

    private final DeleteSupplyService service;

    public DeleteSupplyController(DeleteSupplyService service) {
        this.service = service;
    }

    @DeleteMapping("/supplies/{supplyId}")
    @Operation(
            summary = "Removes a supply by ID",
            description = """
                    Deletes the supply with its contract, distributor and Shelly details.
                    **Required: Community Admin of the supply's community.** A platform admin who does not administer it is answered 404, as for any other access to a supply.

                    A supply that a plant is attached to, or for which a sharing agreement holds partition coefficients, is not deleted: the request is answered 409 with `SUPPLY_HAS_PLANTS` or `SUPPLY_HAS_PARTITION_COEFFICIENTS`, and nothing is deleted. To stop using such a supply, disable it instead (`POST /api/v1/supplies/{supplyId}/disable`).

                    The supply's consumption and production series are not deleted.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "deleteSupply",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Supply deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A plant is attached to the supply, or a sharing agreement holds partition coefficients for it. Nothing is deleted. To stop using the supply, disable it instead.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Has plants",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-17T10:10:25.534035352+02:00",
                                                       "status": 409,
                                                       "message": "A plant is attached to the supply with ID '3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b', so it cannot be deleted. To stop using it, disable the supply instead.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "A plant is attached to the supply with ID '3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b', so it cannot be deleted. To stop using it, disable the supply instead.",
                                                           "code": "SUPPLY_HAS_PLANTS",
                                                           "params": {
                                                             "supplyId": "3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b"
                                                           }
                                                         }
                                                       ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Has partition coefficients",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-17T10:10:25.534035352+02:00",
                                                       "status": 409,
                                                       "message": "A sharing agreement holds partition coefficients for the supply with ID '3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b', so it cannot be deleted. To stop using it, disable the supply instead.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "A sharing agreement holds partition coefficients for the supply with ID '3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b', so it cannot be deleted. To stop using it, disable the supply instead.",
                                                           "code": "SUPPLY_HAS_PARTITION_COEFFICIENTS",
                                                           "params": {
                                                             "supplyId": "3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b"
                                                           }
                                                         }
                                                       ]
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canEditSupply(#supplyId)")
    public void deleteSupply(@PathVariable("supplyId") UUID supplyId) {
        service.delete(SupplyId.of(supplyId));
    }
}
