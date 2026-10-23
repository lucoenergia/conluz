package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.admins.EnableCommunityAdminService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/admins")
public class EnableCommunityAdminController {

    private final EnableCommunityAdminService service;

    public EnableCommunityAdminController(EnableCommunityAdminService service) {
        this.service = service;
    }

    @PostMapping("/{userId}/enable")
    @Operation(
            summary = "Enables a community admin's membership.",
            description = """
                    Enables a community admin's membership of this community, so that it grants admin access again once it is accepted. Only the membership is written, never the account. Enabling an enabled membership changes nothing. Only memberships with the COMMUNITY_ADMIN role: any other is answered 404. Only a \
                    platform admin may enable them.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "enableCommunityAdmin",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Community admin membership enabled"
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunityAdmins(#communityId)")
    public void enableCommunityAdmin(@AuthenticationPrincipal User currentUser,
                                    @PathVariable("communityId") UUID communityId,
                                    @PathVariable("userId") UUID userId) {
        service.enable(communityId, UserId.of(userId), UserId.of(currentUser.getId()));
    }
}
