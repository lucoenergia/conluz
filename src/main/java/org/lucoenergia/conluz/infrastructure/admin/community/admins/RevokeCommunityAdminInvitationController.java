package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.admins.RevokeCommunityAdminInvitationService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/admins",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class RevokeCommunityAdminInvitationController {

    private final RevokeCommunityAdminInvitationService service;

    public RevokeCommunityAdminInvitationController(RevokeCommunityAdminInvitationService service) {
        this.service = service;
    }

    @PostMapping("/{userId}/invitation/revoke")
    @Operation(
            summary = "Revokes a community admin's invitation.",
            description = """
                    Stops the admin's invitation link from working. The membership stays pending with its \
                    invitation instant, and can be sent a new link with `resendCommunityAdminInvitation`. The \
                    rules are those of `revokeInvitation`: it works whether or not the membership is enabled and \
                    whether or not emails can be sent, and an accepted membership responds 409 with the \
                    `MEMBERSHIP_ALREADY_EXISTS` code.

                    Only memberships with the COMMUNITY_ADMIN role: any other is answered 404. Only a platform \
                    admin may revoke them.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "revokeCommunityAdminInvitation",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Invitation revoked, or there was no link to revoke",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The membership has been accepted (`MEMBERSHIP_ALREADY_EXISTS`).",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class)
                    )
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunityAdmins(#communityId)")
    public CommunityAdminResponse revokeCommunityAdminInvitation(@PathVariable("communityId") UUID communityId,
                                                                 @PathVariable("userId") UUID userId) {
        return new CommunityAdminResponse(service.revoke(communityId, UserId.of(userId)));
    }
}
