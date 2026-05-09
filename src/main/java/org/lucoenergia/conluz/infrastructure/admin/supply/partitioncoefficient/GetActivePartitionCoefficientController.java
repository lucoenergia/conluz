package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientNotFoundException;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * Returns the currently active partition coefficient for a supply.
 */
@RestController
@RequestMapping(value = "/api/v1/supplies/{supplyId}/partition-coefficients/active",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class GetActivePartitionCoefficientController {

    private final PartitionCoefficientService service;

    public GetActivePartitionCoefficientController(PartitionCoefficientService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Returns the currently active partition coefficient for a supply.",
            description = "Returns the coefficient with validTo = null. **Required Role: ADMIN**",
            tags = ApiTag.SUPPLIES,
            operationId = "getActivePartitionCoefficient",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active coefficient retrieved successfully.", useReturnTypeSchema = true)
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public PartitionCoefficientResponse getActive(
            @Parameter(description = "Supply UUID") @PathVariable UUID supplyId) {
        Optional<SupplyPartitionCoefficient> result = service.findActiveBySupplyId(supplyId);
        if (result.isEmpty()) {
            throw new SupplyPartitionCoefficientNotFoundException(supplyId);
        }
        return new PartitionCoefficientResponse(result.get());
    }
}
