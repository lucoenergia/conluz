package org.lucoenergia.conluz.infrastructure.admin.user.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
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
    private final AuthService authService;

    public GetSuppliesByUserIdController(GetSupplyService supplyService, AuthService authService) {
        this.supplyService = supplyService;
        this.authService = authService;
    }

    @GetMapping("/{id}/supplies")
    @Operation(
            summary = "Retrieves all supplies for a specific user",
            description = """
                    This endpoint retrieves all supplies associated with a specific user by their unique identifier.

                    **Authorization Rules:**
                    - Users with role ADMIN can retrieve supplies for any user
                    - Users with role PARTNER can only retrieve their own supplies

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
    @PreAuthorize("isAuthenticated()")
    public List<SupplyResponse> getSuppliesByUserId(@PathVariable("id") UUID userId) {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User must be authenticated"));

        UserId targetUserId = UserId.of(userId);
        UserId requestingUserId = UserId.of(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        List<Supply> supplies = supplyService.getByUserId(targetUserId, requestingUserId, isAdmin);

        return supplies.stream()
                .map(SupplyResponse::new)
                .toList();
    }
}
