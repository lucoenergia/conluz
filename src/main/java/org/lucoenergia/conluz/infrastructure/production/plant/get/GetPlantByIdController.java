package org.lucoenergia.conluz.infrastructure.production.plant.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
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
    private final CommunityAccessGuard communityAccessGuard;

    public GetPlantByIdController(GetPlantService service, CommunityAccessGuard communityAccessGuard) {
        this.service = service;
        this.communityAccessGuard = communityAccessGuard;
    }

    @GetMapping("/plants/{id}")
    @Operation(
            summary = "Retrieves a single plant by ID",
            description = """
                    This endpoint retrieves detailed information about a specific plant by its unique identifier.

                    **Required: any member of the plant's community (any role).**

                    Returns 404 if the plant does not exist OR if the caller is not a member of its
                    community, to avoid leaking the existence of plants by ID.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.PLANTS,
            operationId = "getPlantById",
            security = @SecurityRequirement(name = "bearerToken")
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
    @PreAuthorize("isAuthenticated()")
    public PlantResponse getPlantById(@PathVariable("id") UUID plantId) {
        // Non-members get a 404 (not 403) so they cannot probe the existence of a plant by its ID.
        if (!communityAccessGuard.canReadPlant(plantId)) {
            throw new PlantNotFoundException(PlantId.of(plantId));
        }
        Plant plant = service.findById(PlantId.of(plantId));
        return new PlantResponse(plant);
    }
}
