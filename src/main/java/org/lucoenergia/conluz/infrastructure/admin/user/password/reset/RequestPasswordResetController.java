package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.user.password.reset.RequestPasswordResetService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.TooManyRequestsErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets anybody ask for a password reset link to be emailed (#362). Public: it answers the same whether or not the
 * personal ID exists, so it tells nothing to whoever calls it.
 */
@RestController
@RequestMapping("/api/v1/users/password")
@Validated
public class RequestPasswordResetController {

    private final RequestPasswordResetService service;

    public RequestPasswordResetController(RequestPasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/recover")
    @Operation(
            summary = "Requests a password reset link by email",
            description = """
                This endpoint starts the recovery of a forgotten password. It requires no authentication, and any
                token presented with it is ignored.

                If the personal ID belongs to an enabled user with an email address who has been sent fewer than
                3 links in the last 24 hours, a link of the form `<web client>/reset-password#<token>` is emailed
                to them. The link is valid for 1 day and works once, and it replaces any link sent before, which
                stops working.

                The answer is always the same 202 with an empty body: for an unknown personal ID, a user without
                email, a disabled user, a user over the daily limit, and when the email cannot be sent. Nothing in
                it tells whether a link was sent.

                Every request counts against the client address, together with failed logins and password
                changes: after 20 within 15 minutes, further requests are answered 429 with a Retry-After header
                until the 15 minutes that started with the first one have passed.""",
            tags = ApiTag.AUTHENTICATION,
            operationId = "requestPasswordReset"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "202",
                    description = "Accepted. Answered the same whether or not a link was sent."
            )
    })
    @BadRequestErrorResponse
    @TooManyRequestsErrorResponse
    @InternalServerErrorResponse
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequestBody body,
                                                     HttpServletRequest request) {
        service.request(body.getPersonalId(), request.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }
}
