package org.lucoenergia.conluz.infrastructure.admin.user.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
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

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesAssembler;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.MemberDataAssembler;

/**
 * Get user by ID
 */
@RestController
@RequestMapping(value = "/api/v1/users")
public class GetUserByIdController {

    private final GetUserService service;
    private final CommunityAccessGuard communityAccessGuard;
    private final UserCapabilitiesAssembler capabilitiesAssembler;
    private final MemberDataAssembler memberDataAssembler;

    public GetUserByIdController(GetUserService service,
                                 CommunityAccessGuard communityAccessGuard,
                                 UserCapabilitiesAssembler capabilitiesAssembler,
                                 MemberDataAssembler memberDataAssembler) {
        this.service = service;
        this.communityAccessGuard = communityAccessGuard;
        this.capabilitiesAssembler = capabilitiesAssembler;
        this.memberDataAssembler = memberDataAssembler;
    }

    @GetMapping("/{userId}")
    @Operation(
            summary = "Retrieves a single user by ID",
            description = """
                    This endpoint retrieves detailed information about a specific user by their unique identifier.

                    **Required: the user themselves, or a Community Admin of one of the user's communities.** Being a Platform Admin is not enough (#446): any other caller is answered 404.

                    **What `memberships` contains:** a user reading themselves sees all of their own; otherwise only the memberships in communities the
                    caller administers.

                    Authentication is required using a Bearer token.
                    """,
            tags = ApiTag.USERS,
            operationId = "getUserById",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User found and returned successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadUser(#userId)")
    public UserResponse getUserById(@AuthenticationPrincipal User currentUser,
                                    @PathVariable("userId") UUID userId) {
        User user = service.findById(UserId.of(userId));
        // GetUserService attaches every membership of the user, and the capabilities are assembled from
        // all of them; the scope narrows what the response shows only afterwards, so it cannot move one.
        return new UserResponse(user, memberDataAssembler.forUserWithLoadedMemberships(currentUser, user),
                capabilitiesAssembler.assembleWithLoadedMemberships(currentUser, user),
                communityAccessGuard.visibleUsers());
    }
}
