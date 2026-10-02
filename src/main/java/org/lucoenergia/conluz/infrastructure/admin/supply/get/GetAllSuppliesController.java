package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationRequestMapper;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springdoc.core.converters.models.PageableAsQueryParam;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.SupplyCapabilitiesAssembler;
import java.util.Objects;
import java.util.Map;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesAssembler;

/**
 * Get the supplies of a community visible to the current user. The caller must be a member of the
 * community; a non-member platform admin is denied.
 */
@RestController
@RequestMapping(value = "/api/v1/communities/{communityId}/supplies")
public class GetAllSuppliesController {

    private final GetSupplyService service;
    private final PaginationRequestMapper paginationRequestMapper;
    private final SupplyCapabilitiesAssembler capabilitiesAssembler;
    private final UserCapabilitiesAssembler userCapabilitiesAssembler;
    private final CommunityAccessGuard communityAccessGuard;

    public GetAllSuppliesController(GetSupplyService service, PaginationRequestMapper paginationRequestMapper,
                                    CommunityAccessGuard communityAccessGuard,
                                    SupplyCapabilitiesAssembler capabilitiesAssembler,
                                    UserCapabilitiesAssembler userCapabilitiesAssembler) {
        this.service = service;
        this.paginationRequestMapper = paginationRequestMapper;
        this.communityAccessGuard = communityAccessGuard;
        this.capabilitiesAssembler = capabilitiesAssembler;
        this.userCapabilitiesAssembler = userCapabilitiesAssembler;
    }


    @GetMapping
    @Operation(
            summary = "Retrieves the supplies of a community visible to the current user, with pagination support.",
            description = """
                    Retrieves the supplies of the community identified by the path `communityId`, with pagination,
                    filtering and sorting. Requires authentication through a Bearer Token.

                    **Visibility:** Community admins of the community see all of its supplies.
                    Regular members see only the supplies they own within the community. **Required: any member of the
                    community.** Returns 404 if the community does not exist or the caller is not a member of it, and
                    403 for a non-member platform admin.""",
            tags = ApiTag.SUPPLIES,
            operationId = "getAllSupplies"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Query executed successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PageableAsQueryParam
    @PreAuthorize("@communityAccessGuard.canListSupplies(#communityId)")
    public PagedResult<SupplyResponse> getAllSupplies(@AuthenticationPrincipal User currentUser,
                                                      @PathVariable UUID communityId,
                                                      @Parameter(hidden = true) Pageable page) {
        boolean canSeeAll = communityAccessGuard.adminCommunityIds().contains(communityId);

        PagedResult<Supply> supplies;
        if (canSeeAll) {
            supplies = service.findByCommunity(paginationRequestMapper.mapRequest(page), communityId);
        } else {
            supplies = service.findByOwnerAndCommunity(paginationRequestMapper.mapRequest(page),
                    UserId.of(currentUser.getId()), communityId);
        }

        // One query for every owner on this page, not one per supply: the owners embedded in a
        // supply carry no memberships, and the rules deciding what may be done with them need those.
        Map<UUID, UserCapabilitiesResponse> ownerCapabilities =
                userCapabilitiesAssembler.assembleAllFetchingMemberships(currentUser,
                        supplies.getItems().stream().map(Supply::getUser).filter(Objects::nonNull).toList());

        List<SupplyResponse> suppliesResponse = supplies.getItems().stream()
                .map(supply -> new SupplyResponse(supply, capabilitiesAssembler.assemble(currentUser, supply),
                        ownerCapabilitiesOf(supply, ownerCapabilities)))
                .toList();

        return new PagedResult<>(suppliesResponse, supplies.getSize(), supplies.getTotalElements(),
                supplies.getTotalPages(), supplies.getNumber());
    }

    private UserCapabilitiesResponse ownerCapabilitiesOf(Supply supply,
                                                         Map<UUID, UserCapabilitiesResponse> byUserId) {
        return supply.getUser() == null ? null : byUserId.get(supply.getUser().getId());
    }
}
