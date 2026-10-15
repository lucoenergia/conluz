package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.admins.DisableCommunityAdminService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/admins")
public class DisableCommunityAdminController {

    private final DisableCommunityAdminService service;

    public DisableCommunityAdminController(DisableCommunityAdminService service) {
        this.service = service;
    }

    @PostMapping("/{userId}/disable")
    @Operation(
            summary = "Disables a community admin's membership.",
            description = """
                    Disables a community admin's membership of this community: from the admin's next request it grants no access to this community, while their other communities are unaffected. Only the membership is written, never the account. Disabling a disabled membership changes nothing. Only memberships with the COMMUNITY_ADMIN role: any other is answered 404. Only a \
                    platform admin may disable them.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "disableCommunityAdmin",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Community admin membership disabled"
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunityAdmins(#communityId)")
    public void disableCommunityAdmin(@PathVariable("communityId") UUID communityId,
                                      @PathVariable("userId") UUID userId) {
        service.disable(communityId, UserId.of(userId));
    }
}
