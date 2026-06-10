package org.lucoenergia.conluz.infrastructure.admin.user.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Get supplies for a specific user
 */
@RestController
@RequestMapping(value = "/api/v1/users")
public class GetSuppliesByUserIdController {

    private final GetSupplyService supplyService;

    public GetSuppliesByUserIdController(GetSupplyService supplyService) {
        this.supplyService = supplyService;
    }

    @GetMapping("/{id}/supplies")
    @Operation(
            summary = "Retrieves all supplies for a specific user",
            description = """
                    This endpoint retrieves all supplies associated with a specific user by their unique identifier.

                    **Authorization Rules:**
                    - Platform admins and Community Admins (of the target user's community) can retrieve supplies for that user
                    - A user can retrieve their own supplies

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.USERS,
            operationId = "getSuppliesByUserId",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Supplies retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadUser(#userId)")
    public List<SupplyResponse> getSuppliesByUserId(@PathVariable("id") UUID userId) {
        List<Supply> supplies = supplyService.getByUserId(UserId.of(userId));

        return supplies.stream()
                .map(SupplyResponse::new)
                .toList();
    }
}
