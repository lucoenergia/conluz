package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Updates an existing supply
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class UpdateSupplyController {

    private final UpdateSupplyService service;

    public UpdateSupplyController(UpdateSupplyService service) {
        this.service = service;
    }

    @PutMapping("/supplies/{id}")
    @Operation(
            summary = "Updates supply information",
            description = """
                This endpoint enables the update of supply information by specifying the supply's unique identifier in the endpoint path.
                
                Clients send a request containing the updated supply details, and authentication, through an authentication token, is required for secure access.
                
                A successful update results in an HTTP status code of 200, indicating that the supply information has been successfully modified. In cases where the update encounters errors, the server responds with an appropriate error status code along with a descriptive error message to assist clients in addressing and resolving the issue.
                
                If you don't provide some of the optional parameters, they will be considered as null value so their values will be updated with a null value.""",
            tags = ApiTag.SUPPLIES,
            operationId = "updateSupply"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Supply successfully updated.",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    public SupplyResponse updateSupply(@PathVariable("id") UUID supplyId, @Valid @RequestBody UpdateSupplyBody body) {
        return new SupplyResponse(service.update(SupplyId.of(supplyId), body.mapToSupply()));
    }
}
