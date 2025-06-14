package org.lucoenergia.conluz.infrastructure.production.plant.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

    public CreatePlantController(CreatePlantService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Creates a new plant within the system.",
            description = """
                    This endpoint facilitates the creation of a new plant within the system.
                    
                    To utilize this endpoint, a client sends a request containing essential details such as the plants's
                    address, its code and any relevant parameters.
                    
                    Proper authentication, through authentication tokens, is required to access this endpoint.
                    **Required Role: ADMIN**
                    
                    Upon successful creation, the server responds with a status code of 200, providing comprehensive
                    details about the newly created plant, including its unique identifier.
                    
                    In case of failure, the server returns an appropriate error status code along with a descriptiv
                    error message, aiding the client in diagnosing and addressing the issue.
                    """,
            tags = ApiTag.PLANTS,
            operationId = "createPlant",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The plant has been successfully created.",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public PlantResponse createPlant(@Valid @RequestBody CreatePlantBody body) {
        Plant newPlant = service.create(body.mapToPlant(), SupplyCode.of(body.getSupplyCode()));
        return new PlantResponse(newPlant);
    }
}
