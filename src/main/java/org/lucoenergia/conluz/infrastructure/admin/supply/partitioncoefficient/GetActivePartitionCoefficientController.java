package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
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

import java.util.List;
import java.util.UUID;

/**
 * Returns the active partition coefficients of a supply -- one per plant it participates in.
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
            summary = "Returns the active partition coefficients of a supply, one per plant.",
            description = """
                    Returns one active coefficient per plant the supply participates in. Active means
                    validFrom is set and validTo is not: a pending coefficient (never applied by the
                    distributor) also has a null validTo and is deliberately excluded.

                    A supply may be active in several plants at once, so this is a list. It is empty
                    when the supply has no active coefficient anywhere, which is a normal result
                    rather than an error.

                    **Required: Community Admin of the supply's community.**
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "getActivePartitionCoefficient",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active coefficients retrieved successfully.", useReturnTypeSchema = true)
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canEditSupply(#supplyId)")
    public List<PartitionCoefficientResponse> getActive(
            @Parameter(description = "Supply UUID") @PathVariable UUID supplyId) {
        return service.findActiveBySupplyId(supplyId).stream()
                .map(PartitionCoefficientResponse::new)
                .toList();
    }
}
