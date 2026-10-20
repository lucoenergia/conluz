package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.AcceptInvitationService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.PasswordChangeRequiredErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.TooManyRequestsErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accepts an invitation of the logged-in user's own membership (#394).
 */
@RestController
@RequestMapping("/api/v1/users/current/invitations")
@Validated
public class AcceptInvitationAsCurrentUserController {

    private final AcceptInvitationService service;

    public AcceptInvitationAsCurrentUserController(AcceptInvitationService service) {
        this.service = service;
    }

    @PostMapping("/accept")
    @Operation(
            summary = "Accepts an invitation of the logged-in user",
            description = """
                This endpoint accepts an invitation of one of the caller's own memberships, with the token from
                the emailed link `<web client>/accept-invitation#<token>`. It is the way to accept for an account
                that already has a password; the password is neither read nor changed.

                On success the server answers 204 with no body: the token is used up and the membership is
                accepted. The client should then read the current user again to see the new membership. A
                membership that is disabled is accepted too, but grants no access until the community enables it.

                A token of another account's membership is answered like any unusable token.
                A token that is unknown, malformed, expired, already used, revoked or replaced by a newer one, whose membership
                has already been accepted, or whose account has been disabled, is answered 400 with the
                `MEMBERSHIP_INVITATION_TOKEN_INVALID` code, the same in every case.

                A caller who must change their password is answered 403 with the `USER_PASSWORD_CHANGE_REQUIRED`
                code before the token is looked at; the link stays usable once the password has been changed.

                A platform-admin invitation, `PLATFORM` in `inspectInvitation`, also requires `confirmationCode`:
                the code whoever invited gives the person by another channel. A community invitation ignores it.
                On success the platform-admin role is granted, and, when the account had no email, the
                invitation's destination email becomes its email. A missing or wrong code is answered 400 with the
                `PLATFORM_ADMIN_INVITATION_CODE_INVALID` code: nothing is granted, the attempt counts against the
                client address and against the invitation, and the link can be used again. The fifth missing or
                wrong code on one invitation revokes its link and is answered 400 with the
                `PLATFORM_ADMIN_INVITATION_CODE_ATTEMPTS_EXHAUSTED` code: the invitation must be sent again.

                Every unusable token counts against the client address, together with failed logins, password
                changes and password resets: after 20 within 15 minutes, further requests are answered 429 with a
                Retry-After header until the 15 minutes that started with the first failure have passed.

                **Required: any authenticated user (accepts their own membership).**""",
            tags = ApiTag.MEMBERSHIPS,
            operationId = "acceptInvitationAsCurrentUser",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Invitation accepted."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            The body is invalid, the token cannot be used by the caller \
                            (`MEMBERSHIP_INVITATION_TOKEN_INVALID`), or the confirmation code of a platform-admin \
                            invitation is missing or wrong (`PLATFORM_ADMIN_INVITATION_CODE_INVALID`), for the fifth \
                            time (`PLATFORM_ADMIN_INVITATION_CODE_ATTEMPTS_EXHAUSTED`).""",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Wrong confirmation code",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-20T10:10:25.534035352+02:00",
                                                       "status": 400,
                                                       "message": "The confirmation code is missing or wrong. Ask whoever invited you for it.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The confirmation code is missing or wrong. Ask whoever invited you for it.",
                                                           "code": "PLATFORM_ADMIN_INVITATION_CODE_INVALID",
                                                           "params": null
                                                         }
                                                       ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Too many wrong confirmation codes",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-20T10:10:25.534035352+02:00",
                                                       "status": 400,
                                                       "message": "Too many wrong confirmation codes: the invitation link no longer works. Ask whoever invited you to send the invitation again.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "Too many wrong confirmation codes: the invitation link no longer works. Ask whoever invited you to send the invitation again.",
                                                           "code": "PLATFORM_ADMIN_INVITATION_CODE_ATTEMPTS_EXHAUSTED",
                                                           "params": null
                                                         }
                                                       ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Invalid token",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-15T10:10:25.534035352+02:00",
                                                       "status": 400,
                                                       "message": "The invitation link is invalid or has expired. Ask whoever invited you for a new one.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The invitation link is invalid or has expired. Ask whoever invited you for a new one.",
                                                           "code": "MEMBERSHIP_INVITATION_TOKEN_INVALID",
                                                           "params": null
                                                         }
                                                       ]
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    @UnauthorizedErrorResponse
    @PasswordChangeRequiredErrorResponse
    @TooManyRequestsErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> acceptInvitationAsCurrentUser(@AuthenticationPrincipal User currentUser,
                                                              @Valid @RequestBody AcceptInvitationAsCurrentUserBody body,
                                                              HttpServletRequest request) {
        service.acceptAsUser(UserId.of(currentUser.getId()), body.getToken(), body.getConfirmationCode(),
                request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
