package org.lucoenergia.conluz.infrastructure.admin.supply.enable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.enable.EnableSupplyService;
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
public class EnableSupplyController {

    private final EnableSupplyService service;

    public EnableSupplyController(EnableSupplyService service) {
        this.service = service;
    }

    @PostMapping(path = "/supplies/{id}/enable")
    @Operation(
            summary = "Enables a supply by ID",
            description = """
                    This endpoint enables a supply by its unique identifier.

                    Authentication via bearer token is required.
                    Required Role: ADMIN

                    The operation is idempotent: enabling an already enabled supply will not fail and will return the current state.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "enableSupply",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Supply enabled successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public SupplyResponse enableSupply(@PathVariable("id") UUID supplyId) {
        Supply updated = service.enable(SupplyId.of(supplyId));
        return new SupplyResponse(updated);
    }
}
