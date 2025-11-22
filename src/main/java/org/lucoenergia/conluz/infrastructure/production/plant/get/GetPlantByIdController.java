package org.lucoenergia.conluz.infrastructure.production.plant.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantService;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Get plant by ID
 */
@RestController
@RequestMapping(value = "/api/v1")
public class GetPlantByIdController {

    private final GetPlantService service;

    public GetPlantByIdController(GetPlantService service) {
        this.service = service;
    }

    @GetMapping("/plants/{id}")
    @Operation(
            summary = "Retrieves a single plant by ID",
            description = """
                    This endpoint retrieves detailed information about a specific plant by its unique identifier.

                    **Required Role: ADMIN**

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.PLANTS,
            operationId = "getPlantById",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Plant found and returned successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public PlantResponse getPlantById(@PathVariable("id") UUID plantId) {
        Plant plant = service.findById(PlantId.of(plantId));
        return new PlantResponse(plant);
    }
}
