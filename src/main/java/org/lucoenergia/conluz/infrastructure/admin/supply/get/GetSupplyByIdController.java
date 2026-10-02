package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.SupplyCapabilitiesAssembler;
import java.util.Map;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesAssembler;

/**
 * Controller for retrieving a supply by its ID
 */
@RestController
@RequestMapping(value = "/api/v1/supplies")
public class GetSupplyByIdController {

    private final GetSupplyService service;
    private final SupplyCapabilitiesAssembler capabilitiesAssembler;
    private final UserCapabilitiesAssembler userCapabilitiesAssembler;

    public GetSupplyByIdController(GetSupplyService service,
                                   SupplyCapabilitiesAssembler capabilitiesAssembler,
                                  UserCapabilitiesAssembler userCapabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
        this.userCapabilitiesAssembler = userCapabilitiesAssembler;
    }

    @GetMapping("/{supplyId}")
    @Operation(
            summary = "Gets a supply by ID",
            description = """
                    This endpoint retrieves a supply by its unique identifier.

                    **Required: Community Admin of the supply's community, or the supply owner.**""",
            tags = ApiTag.SUPPLIES,
            operationId = "getSupply"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Supply retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadSupply(#supplyId)")
    public SupplyResponse getSupply(@AuthenticationPrincipal User currentUser,
                                   @PathVariable("supplyId") UUID supplyId) {
        Supply supply = service.getById(SupplyId.of(supplyId));
        return new SupplyResponse(supply, capabilitiesAssembler.assemble(currentUser, supply),
                ownerCapabilities(currentUser, supply));
    }

    /**
     * The owner's capabilities, for the UserResponse embedded in the supply. One supply means one
     * owner, so the single-target lookup is the right shape here; the listings batch instead.
     */
    private UserCapabilitiesResponse ownerCapabilities(User caller, Supply supply) {
        return supply.getUser() == null ? null
                : userCapabilitiesAssembler.assembleFetchingMemberships(caller, supply.getUser());
    }
}
