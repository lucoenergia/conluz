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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Returns the full partition coefficient history for a supply, ordered by validFrom ascending.
 */
@RestController
@RequestMapping(value = "/api/v1/supplies/{supplyId}/partition-coefficients",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class GetPartitionCoefficientHistoryController {

    private final PartitionCoefficientService service;

    public GetPartitionCoefficientHistoryController(PartitionCoefficientService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Returns the full partition coefficient history for a supply.",
            description = """
                    Returns all coefficient periods of the supply, across every plant it participates in,
                    ordered by validFrom ascending. Pending periods (validFrom = null) are included.

                    Each period carries the plant it belongs to, so a supply participating in more than
                    one plant yields several interleaved timelines that a caller can group by plant.
                    Pass plantId to restrict the result to a single plant's timeline; a plant the
                    supply has no coefficient in yields an empty list rather than an error.

                    **Required: Community Admin of the supply's community.**
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "getPartitionCoefficientHistory",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully.", useReturnTypeSchema = true)
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canEditSupply(#supplyId)")
    public List<PartitionCoefficientResponse> getHistory(
            @Parameter(description = "Supply UUID") @PathVariable UUID supplyId,
            @Parameter(description = "Optional plant filter. When omitted, every plant the supply "
                    + "participates in is included.")
            @RequestParam(required = false) UUID plantId) {
        return service.findAllCoefficientHistory(supplyId, plantId).stream()
                .map(PartitionCoefficientResponse::new)
                .toList();
    }
}
