package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.AcceptInvitationService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.TooManyRequestsErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accepts an invitation for an account without a password, setting its first password (#394). Public: the token is
 * the only credential.
 */
@RestController
@RequestMapping("/api/v1/invitations")
@Validated
public class AcceptInvitationController {

    private final AcceptInvitationService service;

    public AcceptInvitationController(AcceptInvitationService service) {
        this.service = service;
    }

    @PostMapping("/accept")
    @Operation(
            summary = "Accepts an invitation, setting the account's first password",
            description = """
                This endpoint accepts the invitation of an account that has no password yet, with the token from
                the emailed link `<web client>/accept-invitation#<token>` and the account's first password. It
                requires no authentication, and any token presented with it is ignored.

                The password must meet the same policy as a password change: between 15 and 64 characters,
                counting each Unicode code point as one, and no more than 72 bytes once UTF-8 encoded.

                On success the server answers 204 with no body: the token is used up, the password is set, the
                membership is accepted, and, when the account had no email, the member record's contact email
                becomes its email. The person is not logged in; the client must log in with the new password. A
                membership that is disabled is accepted too, but grants no access until the community enables it.

                An account that already has a password is answered 409 with the
                `MEMBERSHIP_INVITATION_LOGIN_REQUIRED` code: the person must log in and accept with
                `acceptInvitationAsCurrentUser`. A new password that breaks the policy is answered 400 with the
                `USER_PASSWORD_POLICY_VIOLATION` code and a `rule` parameter. In both cases nothing changes and
                the token can be used again.

                A token that is unknown, malformed, expired, already used, revoked or replaced by a newer one, whose membership
                has already been accepted, or whose account has been disabled, is answered 400 with the
                `MEMBERSHIP_INVITATION_TOKEN_INVALID` code, the same in every case.

                A platform-admin invitation, `PLATFORM` in `inspectInvitation`, also requires `confirmationCode`:
                the code whoever invited gives the person by another channel. A community invitation ignores it.
                On success the platform-admin role is granted as well, and, when the account had no email, the
                invitation's destination email becomes its email. A missing or wrong code is answered 400 with the
                `PLATFORM_ADMIN_INVITATION_CODE_INVALID` code: nothing is granted, the attempt counts against the
                client address and against the invitation, and the link can be used again. The fifth missing or
                wrong code on one invitation revokes its link and is answered 400 with the
                `PLATFORM_ADMIN_INVITATION_CODE_ATTEMPTS_EXHAUSTED` code: the invitation must be sent again. The
                code is checked after the checks above, which neither count nor use it. A platform-admin
                invitation whose account is disabled is accepted too: the role is granted, and the account still
                cannot log in.

                Every unusable token counts against the client address, together with failed logins, password
                changes and password resets: after 20 within 15 minutes, further requests are answered 429 with a
                Retry-After header until the 15 minutes that started with the first failure have passed.""",
            tags = ApiTag.MEMBERSHIPS,
            operationId = "acceptInvitation"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Invitation accepted and first password set. No session is opened."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            The body is invalid, the token cannot be used (`MEMBERSHIP_INVITATION_TOKEN_INVALID`), \
                            the new password breaks the password policy (`USER_PASSWORD_POLICY_VIOLATION`, \
                            with the failed rule in `params.rule`), or the confirmation code of a platform-admin \
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
                                                       "message": "The invitation link is invalid or has expired. Please ask your community for a new one.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The invitation link is invalid or has expired. Please ask your community for a new one.",
                                                           "code": "MEMBERSHIP_INVITATION_TOKEN_INVALID",
                                                           "params": null
                                                         }
                                                       ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Password policy violation",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-15T10:10:25.534035352+02:00",
                                                       "status": 400,
                                                       "message": "The password must be at least 15 characters long.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The password must be at least 15 characters long.",
                                                           "code": "USER_PASSWORD_POLICY_VIOLATION",
                                                           "params": {
                                                             "rule": "TOO_SHORT"
                                                           }
                                                         }
                                                       ]
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = """
                            The account already has a password: log in and accept with \
                            `acceptInvitationAsCurrentUser`. Nothing changed and the token is still usable.""",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Login required",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-15T10:10:25.534035352+02:00",
                                                       "status": 409,
                                                       "message": "This invitation is for an account that already has a password. Log in to accept it.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "This invitation is for an account that already has a password. Log in to accept it.",
                                                           "code": "MEMBERSHIP_INVITATION_LOGIN_REQUIRED",
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
    @TooManyRequestsErrorResponse
    @InternalServerErrorResponse
    public ResponseEntity<Void> acceptInvitation(@Valid @RequestBody AcceptInvitationBody body,
                                                 HttpServletRequest request) {
        service.acceptWithNewPassword(body.getToken(), body.getNewPassword(), body.getConfirmationCode(),
                request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
