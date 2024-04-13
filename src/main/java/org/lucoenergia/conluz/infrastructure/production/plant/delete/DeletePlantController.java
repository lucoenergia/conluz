package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.production.plant.delete.DeletePlantService;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
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

    @DeleteMapping("/plants/{id}")
    @Operation(
            summary = "Removes a plant by ID",
            description = """
                    This endpoint enables the removal of a plant from the system by specifying the plant's unique identifier within the endpoint path.
                    
                    To utilize this endpoint, clients send a DELETE request with the targeted plant's ID, requiring authentication for secure access.
                    
                    Upon successful deletion, the server responds with an HTTP status code of 200, indicating that the plant has been successfully removed.
                    
                    In cases where the deletion process encounters errors, the server returns an appropriate error status code, along with a descriptive error message to guide clients in diagnosing and addressing the issue.
                """,
            tags = ApiTag.PLANTS,
            operationId = "deletePlant"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Plant deleted successfully"
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    public void deletePlant(@PathVariable("id") UUID plantId) {
        service.delete(PlantId.of(plantId));
    }
}
