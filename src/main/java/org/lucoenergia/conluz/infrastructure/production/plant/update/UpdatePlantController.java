package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.springframework.http.MediaType;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantService;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.PlantCapabilitiesAssembler;

/**
 * Updates an existing plant
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class UpdatePlantController {

    private final UpdatePlantService service;
    private final PlantCapabilitiesAssembler capabilitiesAssembler;

    public UpdatePlantController(UpdatePlantService service,
                                 PlantCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PutMapping("/plants/{plantId}")
    @Operation(
            summary = "Updates plant information",
            description = """
                This endpoint enables the update of plant information by specifying the plant's unique identifier in the
                endpoint path.
                
                Clients send a request containing the updated plant details, and authentication, through an
                authentication token, is required for secure access.
                **Required: Community Admin**
                
                A successful update results in an HTTP status code of 200, indicating that the plant information has
                been successfully modified. In cases where the update encounters errors, the server responds with an appropriate error status code along with a descriptive error message to assist clients in addressing and resolving the issue.
                
                If you don't provide some of the optional parameters, they will be considered as null value so their
                values will be updated with a null value.

                A supply is the supply of at most one plant: moving the plant to a supply that already has one is
                answered 409 with `SUPPLY_ALREADY_HAS_PLANT`, and nothing is updated.
                """,
            tags = ApiTag.PLANTS,
            operationId = "updatePlant",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Plant successfully updated.",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The supply is already the supply of another plant. Nothing is written.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    name = "Supply already has a plant",
                                    value = """
                                            {
                                               "timestamp": "2026-10-29T10:10:25.534035352+01:00",
                                               "status": 409,
                                               "message": "The supply with ID '9a7e3c51-2b4d-4f6e-8a1b-3c5d7e9f0a2b' is already the supply of a plant, so no other plant can use it.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "The supply with ID '9a7e3c51-2b4d-4f6e-8a1b-3c5d7e9f0a2b' is already the supply of a plant, so no other plant can use it.",
                                                   "code": "SUPPLY_ALREADY_HAS_PLANT",
                                                   "params": {
                                                     "supplyId": "9a7e3c51-2b4d-4f6e-8a1b-3c5d7e9f0a2b"
                                                   }
                                                 }
                                               ]
                                            }
                                            """
                            )
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManagePlant(#plantId)")
    public PlantResponse updatePlant(@AuthenticationPrincipal User currentUser,
                                     @PathVariable("plantId") UUID plantId,
                                     @Valid @RequestBody UpdatePlantBody body) {
        Plant updated = service.update(body.toPlant(plantId));
        return new PlantResponse(updated, capabilitiesAssembler.assemble(currentUser, updated));
    }
}
