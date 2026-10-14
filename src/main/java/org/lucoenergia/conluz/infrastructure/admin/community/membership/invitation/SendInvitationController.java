package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SendInvitationService;
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
public class SendInvitationController {

    private final SendInvitationService service;
    private final MembershipCapabilitiesAssembler capabilitiesAssembler;

    public SendInvitationController(SendInvitationService service,
                                    MembershipCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PostMapping("/{userId}/invite")
    @Operation(
            summary = "Invites a registered member to accept their membership.",
            description = """
                    Emails the member an invitation to accept their pending membership, at the contact email of \
                    the community's member record, never the account's email. The link is valid for 7 days. Only \
                    an active community admin of the community may invite; a platform admin may not.

                    This is the first invitation only. A membership that has already been invited responds 409 \
                    with the `MEMBERSHIP_ALREADY_INVITED` code, one that has been accepted responds 409 with the \
                    `MEMBERSHIP_ALREADY_EXISTS` code, and a member record without a contact email responds 400 \
                    with the `MEMBERSHIP_WITHOUT_CONTACT_EMAIL` code.

                    If the server has no public web URL configured, nothing is sent and `invitedAt` stays null.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "sendInvitation",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Invitation sent",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The membership has already been invited, or has been accepted.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-09-16T10:10:25.534035352+02:00",
                                               "status": 409,
                                               "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' has already been invited to the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b'.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' has already been invited to the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b'.",
                                                   "code": "MEMBERSHIP_ALREADY_INVITED",
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
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canInviteMembers(#communityId)")
    public MemberResponse sendInvitation(@AuthenticationPrincipal User currentUser,
                                         @PathVariable("communityId") UUID communityId,
                                         @PathVariable("userId") UUID userId) {
        CommunityMembership membership = service.send(communityId, UserId.of(userId));
        return new MemberResponse(membership.getUser().getPersonalId(), membership,
                capabilitiesAssembler.assemble(currentUser, membership));
    }
}
