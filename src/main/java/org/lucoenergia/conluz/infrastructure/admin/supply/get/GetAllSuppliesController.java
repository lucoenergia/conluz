package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springdoc.core.converters.models.PageableAsQueryParam;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Get all supplies registered in the energy community
 */
@RestController
@RequestMapping(value = "/api/v1/supplies")
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
            summary = "Retrieves the supplies visible to the current user with support for pagination, filtering, and sorting.",
            description = """
                    Retrieves supplies with pagination, filtering and sorting. Requires authentication through a Bearer Token.

                    **Visibility:** Platform admins see all supplies. Community admins see supplies of the communities they
                    administer. Regular users see only the supplies they own.""",
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
    @InternalServerErrorResponse
    @PageableAsQueryParam
    @PreAuthorize("isAuthenticated()")
    public PagedResult<SupplyResponse> getAllSupplies(@Parameter(hidden = true) Pageable page) {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User must be authenticated"));
        Set<UUID> adminCommunityIds = communityAccessGuard.adminCommunityIds();

        PagedResult<Supply> supplies;
        if (Boolean.TRUE.equals(currentUser.isPlatformAdmin())) {
            supplies = service.findAll(paginationRequestMapper.mapRequest(page));
        } else {
            supplies = service.findAllVisible(paginationRequestMapper.mapRequest(page),
                    UserId.of(currentUser.getId()), adminCommunityIds);
        }

        List<SupplyResponse> suppliesResponse = supplies.getItems().stream()
                .map(SupplyResponse::new)
                .toList();

        return new PagedResult<>(suppliesResponse, supplies.getSize(), supplies.getTotalElements(),
                supplies.getTotalPages(), supplies.getNumber());
    }
}
