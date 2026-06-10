package org.lucoenergia.conluz.infrastructure.admin.user.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserService;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
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
 * Get all users — platform admin sees all; community admin sees only their communities' users.
 */
@RestController
@RequestMapping(value = "/api/v1/users")
public class GetAllUsersController {

    private final GetUserService service;
    private final PaginationRequestMapper paginationRequestMapper;
    private final CommunityAccessGuard communityAccessGuard;
    private final AuthService authService;

    public GetAllUsersController(GetUserService service, PaginationRequestMapper paginationRequestMapper,
                                 CommunityAccessGuard communityAccessGuard, AuthService authService) {
        this.service = service;
        this.paginationRequestMapper = paginationRequestMapper;
        this.communityAccessGuard = communityAccessGuard;
        this.authService = authService;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves all registered users in the system with support for pagination, filtering, and sorting.",
            description = """
                    This endpoint facilitates the retrieval of all users within the system, allowing clients to access a
                    comprehensive list of user details.

                    **Required: Platform Admin or Community Admin**
                    Platform admins see all users; community admins see only users belonging to their communities.

                    Features:
                    - Pagination support through page and limit parameters
                    - Sorting options for customized result ordering

                    Authentication:
                    - Requires valid authentication token
                    - Bearer token authorization
                    """,
            tags = ApiTag.USERS,
            operationId = "getAllUsers",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
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
    @PreAuthorize("@communityAccessGuard.canListUsers()")
    public PagedResult<UserResponse> getAllUsers(@Parameter(hidden = true) Pageable page) {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("User must be authenticated"));
        Set<UUID> visibleCommunityIds = communityAccessGuard.visibleCommunityIds();

        PagedResult<User> users;
        if (Boolean.TRUE.equals(currentUser.isPlatformAdmin())) {
            users = service.findAll(paginationRequestMapper.mapRequest(page));
        } else {
            users = service.findAllByCommunities(paginationRequestMapper.mapRequest(page), visibleCommunityIds);
        }

        List<UserResponse> responseUsers = users.getItems().stream()
                .map(UserResponse::new).toList();

        return new PagedResult<>(responseUsers, users.getSize(), users.getTotalElements(), users.getTotalPages(),
                users.getNumber());
    }
}
