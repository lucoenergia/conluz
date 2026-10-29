package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.BulkSendInvitationService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
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
import org.springframework.web.bind.annotation.RequestBody;
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
            summary = "Sends the first invitation to members registered without one, or to the selected members, up "
                    + "to 50 per request.",
            description = """
                    Invites, as `sendInvitation` does, the members of the community who are pending, enabled, have \
                    a contact email and have never been invited, up to 50 per request, in ascending user id. Every \
                    other member is skipped, not refused: an accepted or disabled membership, one without a \
                    contact email, and one already invited, including by a concurrent request. Each member is \
                    invited on their own, so a failure keeps the invitations already sent. Call again while \
                    `remaining` is above zero. Only an active community admin of the community may send; a \
                    platform admin may not.

                    With a body, it acts on exactly the selected members instead, 1 to 50 distinct user ids, in \
                    ascending user id: a member never invited is sent their first invitation, as `sendInvitation` \
                    does, and a member already invited has it resent, as `resendInvitation` does, within its limit. \
                    Any other member is refused with the code and parameters those endpoints answer, and a user who \
                    is not a member of the community with `MEMBERSHIP_NOT_FOUND`, whether or not the account \
                    exists. Each member is processed on their own, so a refusal never stops the others. `results` \
                    holds one entry per selected user, in the order requested; `sent` counts the first invitations \
                    among them, and `remaining` keeps its meaning. An empty selection, more than 50 user ids, or a \
                    user id named twice is refused with 400 and `MEMBERSHIP_INVITATION_SELECTION_EMPTY`, \
                    `MEMBERSHIP_INVITATION_SELECTION_TOO_LARGE` (with the limit in `params.maxUserIds`) or \
                    `MEMBERSHIP_INVITATION_SELECTION_DUPLICATED`, and nobody is invited.

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
                    responseCode = "400",
                    description = "The request is invalid: the selection is empty, holds more than 50 user ids, "
                            + "names one twice, or is not well formed. Nobody was invited.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-09-16T10:10:25.534035352+02:00",
                                               "status": 400,
                                               "message": "Select at most 50 members to invite at once. Nobody has been invited.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "Select at most 50 members to invite at once. Nobody has been invited.",
                                                   "code": "MEMBERSHIP_INVITATION_SELECTION_TOO_LARGE",
                                                   "params": {
                                                     "maxUserIds": "50"
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
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canInviteMembers(#communityId)")
    public SendInvitationsResponse sendInvitations(@AuthenticationPrincipal User currentUser,
                                                   @PathVariable("communityId") UUID communityId,
                                                   @Valid @RequestBody(required = false) SendInvitationsBody body) {
        UserId sentBy = UserId.of(currentUser.getId());
        // Without a body, the members awaiting their first invitation; with one, the members selected (#527)
        return new SendInvitationsResponse(communityId, body == null
                ? service.send(communityId, sentBy)
                : service.sendTo(communityId, body.getUserIds(), sentBy));
    }
}
