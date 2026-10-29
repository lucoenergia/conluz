package org.lucoenergia.conluz.infrastructure.production.plant.create;

import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.PlantCapabilitiesAssembler;

/**
 * Adds a new plant
 */
@RestController
@RequestMapping(
        value = "/api/v1/plants",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class CreatePlantController {

    private final CreatePlantService service;
    private final PlantCapabilitiesAssembler capabilitiesAssembler;

    public CreatePlantController(CreatePlantService service,
                                 PlantCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PostMapping
    @Operation(
            summary = "Creates a new plant within the system.",
            description = """
                    This endpoint facilitates the creation of a new plant within the system.
                    
                    To utilize this endpoint, a client sends a request containing essential details such as the plants's
                    address, its code and any relevant parameters.
                    
                    Proper authentication, through authentication tokens, is required to access this endpoint.
                    **Required: Community Admin**
                    
                    Upon successful creation, the server responds with a status code of 200, providing comprehensive
                    details about the newly created plant, including its unique identifier.

                    A supply is the supply of at most one plant: when the supply already has one, the request is
                    answered 409 with `SUPPLY_ALREADY_HAS_PLANT`, and nothing is created.
                    
                    In case of failure, the server returns an appropriate error status code along with a descriptiv
                    error message, aiding the client in diagnosing and addressing the issue.
                    """,
            tags = ApiTag.PLANTS,
            operationId = "createPlant",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The plant has been successfully created.",
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
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @PreAuthorize("@communityAccessGuard.canCreatePlant(#body.supplyCode)")
    public PlantResponse createPlant(@AuthenticationPrincipal User currentUser,
                                     @Valid @RequestBody CreatePlantBody body) {
        Plant newPlant = service.create(body.mapToPlant(), SupplyCode.of(body.getSupplyCode()));
        return new PlantResponse(newPlant, capabilitiesAssembler.assemble(currentUser, newPlant));
    }
}
