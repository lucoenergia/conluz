package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Returns the coefficient that was active at a given point in time.
 */
@RestController
@RequestMapping(value = "/api/v1/supplies/{supplyId}/partition-coefficients/at",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class GetPartitionCoefficientAtTimestampController {

    private final PartitionCoefficientService service;

    public GetPartitionCoefficientAtTimestampController(PartitionCoefficientService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Returns the coefficient that was active at the given point in time.",
            description = """
                    Uses boundary convention: validFrom inclusive, validTo exclusive.
                    **Required Role: ADMIN**
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "getPartitionCoefficientAtTimestamp",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Coefficient retrieved successfully.", useReturnTypeSchema = true)
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public CoefficientAtTimestampResponse getAtTimestamp(
            @Parameter(description = "Supply UUID") @PathVariable UUID supplyId,
            @Parameter(description = "ISO-8601 timestamp", example = "2025-01-15T12:00:00Z")
            @RequestParam @NotNull Instant timestamp) {
        BigDecimal coefficient = service.findCoefficientByInstant(supplyId, timestamp);
        return new CoefficientAtTimestampResponse(supplyId, timestamp, coefficient);
    }
}
