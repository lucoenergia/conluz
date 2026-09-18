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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.SupplyCapabilitiesAssembler;
import java.util.Objects;
import java.util.Map;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesAssembler;

/**
 * Get supplies for a specific user
 */
@RestController
@RequestMapping(value = "/api/v1/users")
public class GetSuppliesByUserIdController {

    private final GetSupplyService supplyService;
    private final SupplyCapabilitiesAssembler capabilitiesAssembler;
    private final UserCapabilitiesAssembler userCapabilitiesAssembler;

    public GetSuppliesByUserIdController(GetSupplyService supplyService,
                                         SupplyCapabilitiesAssembler capabilitiesAssembler,
                                         UserCapabilitiesAssembler userCapabilitiesAssembler) {
        this.supplyService = supplyService;
        this.capabilitiesAssembler = capabilitiesAssembler;
        this.userCapabilitiesAssembler = userCapabilitiesAssembler;
    }

    @GetMapping("/{userId}/supplies")
    @Operation(
            summary = "Retrieves all supplies for a specific user",
            description = """
                    This endpoint retrieves all supplies associated with a specific user by their unique identifier.

                    **Authorization Rules:**
                    - Community Admins (of the target user's community) can retrieve supplies for that user
                    - A user can retrieve their own supplies
                    - Being a Platform Admin is **not** sufficient: these are supplies, and a Platform Admin
                      who administers none of the user's communities cannot read them one by one either

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
    @PreAuthorize("@communityAccessGuard.canListSuppliesOfUser(#userId)")
    public List<SupplyResponse> getSuppliesByUserId(@AuthenticationPrincipal User currentUser,
                                                    @PathVariable("userId") UUID userId) {
        List<Supply> supplies = supplyService.getByUserId(UserId.of(userId));

        // One query for every owner on this page, not one per supply: the owners embedded in a
        // supply carry no memberships, and the rules deciding what may be done with them need those.
        Map<UUID, UserCapabilitiesResponse> ownerCapabilities =
                userCapabilitiesAssembler.assembleAllFetchingMemberships(currentUser,
                        supplies.stream().map(Supply::getUser).filter(Objects::nonNull).toList());

        return supplies.stream()
                .map(supply -> new SupplyResponse(supply, capabilitiesAssembler.assemble(currentUser, supply),
                        ownerCapabilitiesOf(supply, ownerCapabilities)))
                .toList();
    }

    private UserCapabilitiesResponse ownerCapabilitiesOf(Supply supply,
                                                         Map<UUID, UserCapabilitiesResponse> byUserId) {
        return supply.getUser() == null ? null : byUserId.get(supply.getUser().getId());
    }
}
