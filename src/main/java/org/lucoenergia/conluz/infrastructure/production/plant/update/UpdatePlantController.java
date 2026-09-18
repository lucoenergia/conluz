package org.lucoenergia.conluz.infrastructure.production.plant.update;

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
