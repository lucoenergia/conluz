package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.DisableMembershipService;
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
public class DisableMembershipController {

    private final DisableMembershipService service;

    public DisableMembershipController(DisableMembershipService service) {
        this.service = service;
    }

    @PostMapping("/{userId}/disable")
    @Operation(
            summary = "Disables a member's membership.",
            description = """
                    Disables a member's membership of this community: from the member's next request it grants no access to this community, while their other communities are unaffected, and no invitation is sent to it. Only the membership is written, never the account, which the person shares with their other communities. Disabling a disabled membership changes nothing.

                    This is how a community suspends a member: a community admin cannot disable the account itself (`POST /api/v1/users/{userId}/disable`).

                    Only memberships with the COMMUNITY_MEMBER role, pending or accepted: a membership with any other role, or none, is answered 404. Admin memberships are disabled by the platform admin (`POST /api/v1/communities/{communityId}/admins/{userId}/disable`).

                    **Required: Community Admin of this community.** A platform admin who is not one is refused with 403.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "disableMembership",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Membership disabled"
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canChangeMemberState(#communityId, #userId)")
    public void disableMembership(@PathVariable("communityId") UUID communityId,
                                  @PathVariable("userId") UUID userId) {
        service.disable(communityId, UserId.of(userId));
    }
}
