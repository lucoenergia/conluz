package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationRequestMapper;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springdoc.core.converters.models.PageableAsQueryParam;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Get the supplies of a community visible to the current user.
 */
@RestController
@RequestMapping(value = "/api/v1/communities/{communityId}/supplies")
public class GetAllSuppliesController {

    private final GetSupplyService service;
    private final PaginationRequestMapper paginationRequestMapper;
    private final CommunityAccessGuard communityAccessGuard;
    private final AuthService authService;

    public GetAllSuppliesController(GetSupplyService service, PaginationRequestMapper paginationRequestMapper,
                                    CommunityAccessGuard communityAccessGuard, AuthService authService) {
        this.service = service;
        this.paginationRequestMapper = paginationRequestMapper;
        this.communityAccessGuard = communityAccessGuard;
        this.authService = authService;
    }


    @GetMapping
    @Operation(
            summary = "Retrieves the supplies of a community visible to the current user, with pagination support.",
            description = """
                    Retrieves the supplies of the community identified by the path `communityId`, with pagination,
                    filtering and sorting. Requires authentication through a Bearer Token.

                    **Visibility:** Platform admins and Community admins of the community see all of its supplies.
                    Regular members see only the supplies they own within the community. Returns 404 if the community
                    does not exist or the caller is not a member of it.""",
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
    @PreAuthorize("isAuthenticated()")
    public PagedResult<SupplyResponse> getAllSupplies(@PathVariable UUID communityId,
                                                      @Parameter(hidden = true) Pageable page) {
        if (!communityAccessGuard.canReadCommunity(communityId)) {
            throw new CommunityNotFoundException(communityId);
        }

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User must be authenticated"));

        boolean canSeeAll = communityAccessGuard.adminCommunityIds().contains(communityId);

        PagedResult<Supply> supplies;
        if (canSeeAll) {
            supplies = service.findByCommunity(paginationRequestMapper.mapRequest(page), communityId);
        } else {
            supplies = service.findByOwnerAndCommunity(paginationRequestMapper.mapRequest(page),
                    UserId.of(currentUser.getId()), communityId);
        }

        List<SupplyResponse> suppliesResponse = supplies.getItems().stream()
                .map(SupplyResponse::new)
                .toList();

        return new PagedResult<>(suppliesResponse, supplies.getSize(), supplies.getTotalElements(),
                supplies.getTotalPages(), supplies.getNumber());
    }
}
