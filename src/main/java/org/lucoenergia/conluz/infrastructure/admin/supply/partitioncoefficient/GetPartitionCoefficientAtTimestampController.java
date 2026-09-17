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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Returns the coefficients that were active at a given point in time -- one per plant.
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
            summary = "Returns the coefficients that were active at the given point in time, one per plant.",
            description = """
                    Returns one entry per plant whose coefficient for the supply covers the given
                    instant. Boundary convention: validFrom inclusive, validTo exclusive, so at an
                    instant shared by two consecutive periods the later one applies.

                    A supply may hold a coefficient in several plants at once, so this is a list, and
                    it is empty when no period covers the instant -- a normal result, not an error.
                    Pass plantId to restrict the result to a single plant.
                    Pending coefficients are excluded: one the distributor never applied covered no
                    instant.

                    **Required: Community Admin of the supply's community.**
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "getPartitionCoefficientAtTimestamp",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Coefficients retrieved successfully.", useReturnTypeSchema = true)
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadSupplyPartitionCoefficients(#supplyId)")
    public List<CoefficientAtTimestampResponse> getAtTimestamp(
            @Parameter(description = "Supply UUID") @PathVariable UUID supplyId,
            @Parameter(description = "ISO-8601 timestamp", example = "2025-01-15T12:00:00Z")
            @RequestParam @NotNull Instant timestamp,
            @Parameter(description = "Optional plant filter. When omitted, every plant the supply "
                    + "participates in is included.")
            @RequestParam(required = false) UUID plantId) {
        return service.findCoefficientsByInstant(supplyId, plantId, timestamp).stream()
                .map(detail -> new CoefficientAtTimestampResponse(detail, timestamp))
                .toList();
    }
}
