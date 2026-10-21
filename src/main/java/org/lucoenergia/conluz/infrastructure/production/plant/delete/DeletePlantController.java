package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.plant.delete.DeletePlantService;
import org.lucoenergia.conluz.domain.shared.PlantId;
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
public class DeletePlantController {

    private final DeletePlantService service;

    public DeletePlantController(DeletePlantService service) {
        this.service = service;
    }

    @DeleteMapping("/plants/{plantId}")
    @Operation(
            summary = "Removes a plant by ID",
            description = """
                    Deletes the plant with its Huawei configuration.
                    **Required: Community Admin of the plant's community.**

                    A plant that a sharing agreement, in any status, or a partition coefficient references is not deleted: the request is answered 409 with `PLANT_HAS_SHARING_AGREEMENTS` or `PLANT_HAS_PARTITION_COEFFICIENTS`, and nothing is deleted. Such a plant holds the community's sharing history and must be kept; a plant cannot be disabled.

                    The plant's production series are not deleted.
                    """,
            tags = ApiTag.PLANTS,
            operationId = "deletePlant",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Plant deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A sharing agreement, in any status, or a partition coefficient references the plant. Nothing is deleted. The plant holds the community's sharing history and must be kept.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Has sharing agreements",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-17T10:10:25.534035352+02:00",
                                                       "status": 409,
                                                       "message": "The plant with ID '3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b' has sharing agreements, so it cannot be deleted. It holds the community's sharing history and must be kept.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The plant with ID '3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b' has sharing agreements, so it cannot be deleted. It holds the community's sharing history and must be kept.",
                                                           "code": "PLANT_HAS_SHARING_AGREEMENTS",
                                                           "params": {
                                                             "plantId": "3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b"
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
                                                       "message": "The plant with ID '3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b' has partition coefficients, so it cannot be deleted. It holds the community's sharing history and must be kept.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The plant with ID '3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b' has partition coefficients, so it cannot be deleted. It holds the community's sharing history and must be kept.",
                                                           "code": "PLANT_HAS_PARTITION_COEFFICIENTS",
                                                           "params": {
                                                             "plantId": "3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b"
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
    @PreAuthorize("@communityAccessGuard.canManagePlant(#plantId)")
    public void deletePlant(@PathVariable("plantId") UUID plantId) {
        service.delete(PlantId.of(plantId));
    }
}
