package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.user.password.reset.ResetPasswordService;
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
 * Sets a new password with the token of a password reset link (#362). Public: the token is the only credential.
 */
@RestController
@RequestMapping("/api/v1/users/password")
@Validated
public class ResetPasswordController {

    private final ResetPasswordService service;

    public ResetPasswordController(ResetPasswordService service) {
        this.service = service;
    }

    @PostMapping("/reset")
    @Operation(
            summary = "Sets a new password with a password reset token",
            description = """
                This endpoint completes the recovery of a forgotten password with the token from the emailed link
                `<web client>/reset-password#<token>`. It requires no authentication, and any token presented with
                it is ignored.

                The new password must meet the same policy as a password change: between 15 and 64 characters,
                counting each Unicode code point as one, and no more than 72 bytes once UTF-8 encoded. It must
                differ from the current password.

                On success the server answers 204 with no body: the token is used up, the password is replaced,
                the "must change password" flag is cleared, and every session opened before is ended. The user is
                not logged in; the client must log in with the new password. The account's failed login attempts
                are forgotten, so a user throttled for them can log in at once.

                A token that is unknown, malformed, expired, already used or replaced by a newer one, or whose user
                has been disabled, is answered 400 with the `USER_PASSWORD_RESET_TOKEN_INVALID` code, the same in
                every case. A new password that breaks the policy is answered 400 with the
                `USER_PASSWORD_POLICY_VIOLATION` code and a `rule` parameter, and one equal to the current password
                with the `USER_PASSWORD_UNCHANGED` code; in both cases nothing changes and the token can be used
                again.

                Every invalid token counts against the client address, together with failed logins and password
                changes: after 20 within 15 minutes, further resets are answered 429 with a Retry-After header
                until the 15 minutes that started with the first failure have passed.""",
            tags = ApiTag.AUTHENTICATION,
            operationId = "resetPassword"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Password reset. Every previously issued session token is now rejected."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            The body is invalid, the token cannot be used (`USER_PASSWORD_RESET_TOKEN_INVALID`), \
                            the new password is equal to the current one (`USER_PASSWORD_UNCHANGED`), or the new \
                            password breaks the password policy (`USER_PASSWORD_POLICY_VIOLATION`, with the failed \
                            rule in `params.rule`).""",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Invalid token",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-04T10:10:25.534035352+02:00",
                                                       "status": 400,
                                                       "message": "The password reset link is invalid or has expired. Please request a new one.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The password reset link is invalid or has expired. Please request a new one.",
                                                           "code": "USER_PASSWORD_RESET_TOKEN_INVALID",
                                                           "params": null
                                                         }
                                                       ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Password unchanged",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-04T10:10:25.534035352+02:00",
                                                       "status": 400,
                                                       "message": "The new password must be different from the current one.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The new password must be different from the current one.",
                                                           "code": "USER_PASSWORD_UNCHANGED",
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
                                                       "timestamp": "2026-10-04T10:10:25.534035352+02:00",
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
            )
    })
    @TooManyRequestsErrorResponse
    @InternalServerErrorResponse
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetBody body,
                                              HttpServletRequest request) {
        service.reset(body.getToken(), body.getNewPassword(), request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
