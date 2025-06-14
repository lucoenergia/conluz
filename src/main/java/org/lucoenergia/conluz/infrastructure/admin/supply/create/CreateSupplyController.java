package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adds a new supply
 */
@RestController
@RequestMapping(
        value = "/api/v1/supplies",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class CreateSupplyController {

    private final CreateSupplyService service;

    public CreateSupplyController(CreateSupplyService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Creates a new supply within the system.",
            description = """
                    This endpoint is designed to create a new supply within the system.
                    
                    To utilize this endpoint, a client sends a request containing essential details such as the supply's address, partition coefficient, and any relevant parameters.
                    
                    Proper authentication, through authentication tokens, is required to access this endpoint.
                    **Required Role: ADMIN**
                    
                    Upon successful creation, the server responds with a status code of 200, providing comprehensive details about the newly created supply, including its unique identifier.
                    
                    In case of failure, the server returns an appropriate error status code along with a descriptive error message, aiding the client in diagnosing and addressing the issue. This endpoint plays a pivotal role in dynamically expanding the system's repertoire of energy supplies.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "createSupply",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The supply has been successfully created.",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public SupplyResponse createSupply(@Valid @RequestBody CreateSupplyBody body) {
        Supply newSupply = service.create(body.mapToSupply(), UserPersonalId.of(body.getPersonalId()));
        return new SupplyResponse(newSupply);
    }
}
