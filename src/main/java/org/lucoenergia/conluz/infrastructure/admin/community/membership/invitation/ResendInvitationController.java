package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.ResendInvitationService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.MembershipCapabilitiesAssembler;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.MemberResponse;
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
        value = "/api/v1/communities/{communityId}/memberships",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class ResendInvitationController {

    private final ResendInvitationService service;
    private final MembershipCapabilitiesAssembler capabilitiesAssembler;

    public ResendInvitationController(ResendInvitationService service,
                                      MembershipCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PostMapping("/{userId}/invitation/resend")
    @Operation(
            summary = "Sends a member's invitation again, with a new link.",
            description = """
                    Emails the member a new invitation link, at the contact email the community's member record \
                    has now, and stops the previous link from working, whether it was still valid, had expired or \
                    had been revoked. The new link is valid for 7 days, and `invitedAt` becomes the resend's \
                    instant. Only an active community admin of the community may resend; a platform admin may not.

                    A membership is sent at most 4 invitations in any 24 hours, the first included. Over that, \
                    it responds 429 with the `MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED` code and the number of \
                    seconds until a resend is accepted again in the `Retry-After` header and in \
                    `params.retryAfterSeconds`.

                    A membership never invited responds 409 with the `MEMBERSHIP_NOT_INVITED` code (send its first \
                    invitation instead), one that has been accepted responds 409 with the \
                    `MEMBERSHIP_ALREADY_EXISTS` code, and a member record without a contact email responds 400 \
                    with the `MEMBERSHIP_WITHOUT_CONTACT_EMAIL` code.

                    If the server cannot send emails right now (no public web URL configured, or email sending \
                    disabled), it responds 503 with the `MEMBERSHIP_INVITATION_UNAVAILABLE` code before anything \
                    is recorded: the previous link and `invitedAt` stay as they were.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "resendInvitation",
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
                    description = "The membership has never been invited, or has been accepted.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-09-16T10:10:25.534035352+02:00",
                                               "status": 409,
                                               "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' has not been invited to the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b' yet. Send the first invitation instead.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' has not been invited to the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b' yet. Send the first invitation instead.",
                                                   "code": "MEMBERSHIP_NOT_INVITED",
                                                   "params": {
                                                     "userId": "0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1",
                                                     "communityId": "5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b"
                                                   }
                                                 }
                                               ]
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = """
                            The membership has been sent 4 invitations in the last 24 hours \
                            (`MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED`). Nothing was recorded. Retry after the \
                            number of seconds in the `Retry-After` header, also given in `params.retryAfterSeconds`.""",
                    headers = @Header(
                            name = "Retry-After",
                            description = "Whole seconds until a resend will be accepted again.",
                            required = true,
                            schema = @Schema(type = "integer", format = "int64")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-09-16T10:10:25.534035352+02:00",
                                               "status": 429,
                                               "message": "The invitation of user with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' to the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b' has been sent as many times as allowed in the last 24 hours. Try again in 75601 seconds.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "The invitation of user with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' to the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b' has been sent as many times as allowed in the last 24 hours. Try again in 75601 seconds.",
                                                   "code": "MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED",
                                                   "params": {
                                                     "userId": "0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1",
                                                     "communityId": "5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b",
                                                     "retryAfterSeconds": "75601"
                                                   }
                                                 }
                                               ]
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "The server cannot send invitations right now. Nothing was recorded.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-09-16T10:10:25.534035352+02:00",
                                               "status": 503,
                                               "message": "Invitations cannot be sent right now. Nobody has been invited; try again once email sending is available.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "Invitations cannot be sent right now. Nobody has been invited; try again once email sending is available.",
                                                   "code": "MEMBERSHIP_INVITATION_UNAVAILABLE",
                                                   "params": {}
                                                 }
                                               ]
                                            }
                                            """
                            )
                    )
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canInviteMembers(#communityId)")
    public MemberResponse resendInvitation(@AuthenticationPrincipal User currentUser,
                                           @PathVariable("communityId") UUID communityId,
                                           @PathVariable("userId") UUID userId) {
        MembershipWithInvitation resent = service.resend(communityId, UserId.of(userId));
        CommunityMembership membership = resent.membership();
        return new MemberResponse(membership.getUser().getPersonalId(), membership, resent.invitationExpiresAt(),
                capabilitiesAssembler.assemble(currentUser, membership));
    }
}
