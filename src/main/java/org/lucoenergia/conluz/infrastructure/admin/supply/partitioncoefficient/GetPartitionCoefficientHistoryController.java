package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Returns the partition coefficient history for a supply, ordered by validFrom ascending. How much
 * of it a caller sees depends on whether they administer the supply's community.
 */
@RestController
@RequestMapping(value = "/api/v1/supplies/{supplyId}/partition-coefficients",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class GetPartitionCoefficientHistoryController {

    private final PartitionCoefficientService service;
    private final CommunityAccessGuard communityAccessGuard;

    public GetPartitionCoefficientHistoryController(PartitionCoefficientService service,
                                                    CommunityAccessGuard communityAccessGuard) {
        this.service = service;
        this.communityAccessGuard = communityAccessGuard;
    }

    @GetMapping
    @Operation(
            summary = "Returns the partition coefficient history for a supply.",
            description = """
                    Returns the coefficient periods of the supply, across every plant it participates in,
                    ordered by validFrom ascending.

                    Each period carries the plant it belongs to, so a supply participating in more than
                    one plant yields several interleaved timelines that a caller can group by plant.
                    Pass plantId to restrict the result to a single plant's timeline; a plant the
                    supply has no coefficient in yields an empty list rather than an error.

                    Pending periods (validFrom = null) are authored inside a draft agreement and have
                    never been applied by the distributor. They are returned only to Community Admins
                    of the supply's community; every other caller receives the applied periods alone.

                    **Required: Community Admin of the supply's community, or the supply owner.**
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
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    // Same rule as GET /supplies/{supplyId}: a caller who cannot see the supply gets 404, never 403,
    // so this endpoint has no reachable forbidden outcome and does not document one.
    @PreAuthorize("@communityAccessGuard.canReadSupply(#supplyId)")
    public List<PartitionCoefficientResponse> getHistory(
            @Parameter(description = "Supply UUID") @PathVariable UUID supplyId,
            @Parameter(description = "Optional plant filter. When omitted, every plant the supply "
                    + "participates in is included.")
            @RequestParam(required = false) UUID plantId) {
        // Reading the supply and being able to act on its drafts are different permissions: the guard
        // above admits the owner, and this decides how much of the timeline they are shown.
        boolean includePending = communityAccessGuard.isCommunityAdminOfSupply(supplyId);
        return service.findAllCoefficientHistory(supplyId, plantId, includePending).stream()
                .map(PartitionCoefficientResponse::new)
                .toList();
    }
}
