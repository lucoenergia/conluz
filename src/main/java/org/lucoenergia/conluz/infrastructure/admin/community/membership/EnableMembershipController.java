package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.EnableMembershipService;
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
@RequestMapping("/api/v1/communities/{communityId}/memberships")
public class EnableMembershipController {

    private final EnableMembershipService service;

    public EnableMembershipController(EnableMembershipService service) {
        this.service = service;
    }

    @PostMapping("/{userId}/enable")
    @Operation(
            summary = "Enables a member's membership.",
            description = """
                    Enables a member's membership of this community, so that it grants access to this community again once it is accepted. Only the membership is written, never the account, which the person shares with their other communities. Enabling an enabled membership changes nothing.

                    Only memberships with the COMMUNITY_MEMBER role, pending or accepted: a membership with any other role, or none, is answered 404. Admin memberships are enabled by the platform admin (`POST /api/v1/communities/{communityId}/admins/{userId}/enable`).

                    **Required: Community Admin of this community.** A platform admin who is not one is refused with 403.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "enableMembership",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Membership enabled"
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canChangeMemberState(#communityId, #userId)")
    public void enableMembership(@PathVariable("communityId") UUID communityId,
                                 @PathVariable("userId") UUID userId) {
        service.enable(communityId, UserId.of(userId));
    }
}
