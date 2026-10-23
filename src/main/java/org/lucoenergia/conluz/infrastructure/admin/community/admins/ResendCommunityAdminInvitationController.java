package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.admins.ResendCommunityAdminInvitationService;
import org.lucoenergia.conluz.domain.admin.user.User;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class ResendCommunityAdminInvitationController {

    private final ResendCommunityAdminInvitationService service;

    public ResendCommunityAdminInvitationController(ResendCommunityAdminInvitationService service) {
        this.service = service;
    }

    @PostMapping("/{userId}/invitation/resend")
    @Operation(
            summary = "Sends a community admin's invitation again, with a new link.",
            description = """
                    Emails the admin a new invitation link, at the contact email of this community's member \
                    record, and stops the previous link from working. The rules are those of \
                    `resendInvitation`: at most 4 sends in any 24 hours (429 with the \
                    `MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED` code and a `Retry-After` header), and 409 with \
                    the `MEMBERSHIP_NOT_INVITED`, `MEMBERSHIP_ALREADY_EXISTS` or `MEMBERSHIP_DISABLED` code, 400 \
                    with the `MEMBERSHIP_WITHOUT_CONTACT_EMAIL` code, and 503 with the \
                    `MEMBERSHIP_INVITATION_UNAVAILABLE` code before anything is recorded.

                    Only memberships with the COMMUNITY_ADMIN role: any other is answered 404. Only a platform \
                    admin may resend them.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "resendCommunityAdminInvitation",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Invitation sent again",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The membership has never been invited, has been accepted, or is disabled.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = """
                            The membership has been sent 4 invitations in the last 24 hours \
                            (`MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED`). Nothing was recorded.""",
                    headers = @Header(
                            name = "Retry-After",
                            description = "Whole seconds until a resend will be accepted again.",
                            required = true,
                            schema = @Schema(type = "integer", format = "int64")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "The server cannot send invitations right now (`MEMBERSHIP_INVITATION_UNAVAILABLE`). Nothing was recorded.",
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
    public CommunityAdminResponse resendCommunityAdminInvitation(@AuthenticationPrincipal User currentUser,
                                                                 @PathVariable("communityId") UUID communityId,
                                                                 @PathVariable("userId") UUID userId) {
        return new CommunityAdminResponse(service.resend(communityId, UserId.of(userId),
                UserId.of(currentUser.getId())));
    }
}
