package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.BulkSendInvitationService;
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
        value = "/api/v1/communities/{communityId}/memberships",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class SendInvitationsController {

    private final BulkSendInvitationService service;

    public SendInvitationsController(BulkSendInvitationService service) {
        this.service = service;
    }

    @PostMapping("/invite")
    @Operation(
            summary = "Sends the first invitation to members registered without one, up to 50 per request.",
            description = """
                    Invites, as `sendInvitation` does, the members of the community who are pending, enabled, have \
                    a contact email and have never been invited, up to 50 per request, in ascending user id. Every \
                    other member is skipped, not refused: an accepted or disabled membership, one without a \
                    contact email, and one already invited, including by a concurrent request. Each member is \
                    invited on their own, so a failure keeps the invitations already sent. Call again while \
                    `remaining` is above zero. Only an active community admin of the community may send; a \
                    platform admin may not.

                    If the server cannot send emails right now (no public web URL configured, or email sending \
                    disabled), it responds 503 with the `MEMBERSHIP_INVITATION_UNAVAILABLE` code before anyone is \
                    invited.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "sendInvitations",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Invitations sent, if any were due",
                    useReturnTypeSchema = true
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
    public SendInvitationsResponse sendInvitations(@PathVariable("communityId") UUID communityId) {
        return new SendInvitationsResponse(service.send(communityId));
    }
}
