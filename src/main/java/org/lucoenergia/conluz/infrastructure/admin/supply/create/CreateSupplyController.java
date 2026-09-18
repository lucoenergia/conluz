package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.SupplyCapabilitiesAssembler;
import java.util.UUID;
import java.util.Map;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesAssembler;

/**
 * Adds a new supply
 */
@RestController
@RequestMapping(
        value = "/api/v1/supplies",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class CreateSupplyController {

    private final CreateSupplyService service;
    private final SupplyCapabilitiesAssembler capabilitiesAssembler;
    private final UserCapabilitiesAssembler userCapabilitiesAssembler;

    public CreateSupplyController(CreateSupplyService service,
                                  SupplyCapabilitiesAssembler capabilitiesAssembler,
                                  UserCapabilitiesAssembler userCapabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
        this.userCapabilitiesAssembler = userCapabilitiesAssembler;
    }

    @PostMapping
    @Operation(
            summary = "Creates a new supply within the system.",
            description = """
                    This endpoint is designed to create a new supply within the system.
                    
                    To utilize this endpoint, a client sends a request containing essential details such as the supply's address, partition coefficient, and any relevant parameters.
                    
                    The supply's community is provided in the body via the required `communityId` field.

                    Proper authentication, through authentication tokens, is required to access this endpoint.
                    **Required: Community Admin of the community. Returns 400 if `communityId` is missing, 404 if the
                    community does not exist or the caller is not a member of it, or 403 if the caller is a member but
                    not one of its admins.**

                    Upon successful creation, the server responds with a status code of 200, providing comprehensive details about the newly created supply, including its unique identifier.
                    
                    In case of failure, the server returns an appropriate error status code along with a descriptive error message, aiding the client in diagnosing and addressing the issue. This endpoint plays a pivotal role in dynamically expanding the system's repertoire of energy supplies.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "createSupply",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The supply has been successfully created.",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunity(#body.communityId)")
    public SupplyResponse createSupply(@AuthenticationPrincipal User currentUser,
                                      @Valid @RequestBody CreateSupplyBody body) {
        Supply newSupply = service.create(body.mapToSupply(), UserPersonalId.of(body.getPersonalId()), body.getCommunityId());
        return new SupplyResponse(newSupply, capabilitiesAssembler.assemble(currentUser, newSupply),
                ownerCapabilities(currentUser, newSupply));
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
