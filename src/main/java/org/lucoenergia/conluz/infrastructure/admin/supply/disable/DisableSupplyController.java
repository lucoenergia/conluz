package org.lucoenergia.conluz.infrastructure.admin.supply.disable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.disable.DisableSupplyService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DisableSupplyController {

    private final DisableSupplyService service;

    public DisableSupplyController(DisableSupplyService service) {
        this.service = service;
    }

    @PostMapping(path = "/supplies/{id}/disable")
    @Operation(
            summary = "Disables a supply by ID",
            description = """
                    This endpoint disables a supply by its unique identifier.

                    Authentication via bearer token is required.
                    Required Role: ADMIN

                    The operation is idempotent: disabling an already disabled supply will not fail and will return the current state.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "disableSupply",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Supply disabled successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public SupplyResponse disableSupply(@PathVariable("id") UUID supplyId) {
        Supply updated = service.disable(SupplyId.of(supplyId));
        return new SupplyResponse(updated);
    }
}
