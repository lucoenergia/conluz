package org.lucoenergia.conluz.infrastructure.admin.user.password;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.domain.admin.user.password.ChangePasswordService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.AuthResponseHandler;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAccessTokenHandler;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.TooManyRequestsErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets a user change their own password.
 *
 * <p>Like {@code PUT /users/profile}, it acts on the caller and has no user id to point anywhere, which is why
 * {@code isAuthenticated()} is the whole rule. A wrong current password is a 400, never a 401: clients treat 401
 * as an invalid session and log the user out, and the session here is perfectly valid.</p>
 */
@RestController
@RequestMapping("/api/v1/users/current")
@Validated
public class ChangePasswordController {

    private final ChangePasswordService service;
    private final JwtAccessTokenHandler jwtAccessTokenHandler;
    private final AuthResponseHandler authResponseHandler;

    public ChangePasswordController(ChangePasswordService service, JwtAccessTokenHandler jwtAccessTokenHandler,
                                    AuthResponseHandler authResponseHandler) {
        this.service = service;
        this.jwtAccessTokenHandler = jwtAccessTokenHandler;
        this.authResponseHandler = authResponseHandler;
    }

    @PutMapping("/password")
    @Operation(
            summary = "Changes the password of the current user",
            description = """
                This endpoint lets the authenticated user replace their own password. It always acts on the
                caller, so it cannot be used to change anybody else's password.

                The current password must be supplied and must match. The new password must be between 15 and
                64 characters long, counting each Unicode code point as one, and no more than 72 bytes once
                UTF-8 encoded. Any character is accepted, including spaces and non-ASCII letters; there are no
                composition rules, and the value is never trimmed or transformed. The new password must differ
                from the current one; the comparison is exact, so a value that differs only by case or by leading
                or trailing spaces is a different password.

                On success the server answers 204, clears the "must change password" flag and ends every
                session opened with the previous password: every token issued before the change, including the
                one used for this request, is rejected with 401 from then on, and the access cookie is removed.
                The client must log in again with the new password.

                A wrong current password is answered 400 with the `USER_CURRENT_PASSWORD_INCORRECT` code, never
                401, and changes nothing. A new password that breaks the policy is answered 400 with the
                `USER_PASSWORD_POLICY_VIOLATION` code and a `rule` parameter naming the rule that failed:
                `TOO_SHORT`, `TOO_LONG` or `TOO_MANY_BYTES`. A correct current password with a new password
                exactly equal to it is answered 400 with the `USER_PASSWORD_UNCHANGED` code: nothing is changed,
                the caller's token stays valid and the "must change password" flag stays as it was. A wrong
                current password is reported as such, whatever the new password.

                Neither an unchanged password nor a policy violation counts as a failed attempt. Wrong current
                passwords count together with failed logins on the same account. After 5 failures
                on the account, or 20 from the same client address, within 15 minutes, further changes are
                answered 429 with a Retry-After header, without checking the current password and without
                affecting the caller's token, until the 15 minutes that started with the first failure have
                passed.

                **Required: any authenticated user (changes their own password).**""",
            tags = ApiTag.USERS,
            operationId = "changePassword",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Password changed. Every previously issued token is now rejected."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            The body is invalid, the current password is incorrect \
                            (`USER_CURRENT_PASSWORD_INCORRECT`), the new password is equal to the current one \
                            (`USER_PASSWORD_UNCHANGED`), or the new password breaks the password policy \
                            (`USER_PASSWORD_POLICY_VIOLATION`, with the failed rule in `params.rule`).""",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Incorrect current password",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-04T10:10:25.534035352+02:00",
                                                       "status": 400,
                                                       "message": "The current password is incorrect.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The current password is incorrect.",
                                                           "code": "USER_CURRENT_PASSWORD_INCORRECT",
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
    @UnauthorizedErrorResponse
    @TooManyRequestsErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal User currentUser,
                                               @Valid @RequestBody ChangePasswordBody body,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        // An authenticated request always carries the token it was authenticated with.
        Token usedToken = Token.of(jwtAccessTokenHandler.getTokenFromRequest(request).orElseThrow());

        service.changePassword(UserId.of(currentUser.getId()), body.getCurrentPassword(), body.getNewPassword(),
                usedToken, request.getRemoteAddr());

        authResponseHandler.unsetAccessCookie(response);
        return ResponseEntity.noContent().build();
    }
}
