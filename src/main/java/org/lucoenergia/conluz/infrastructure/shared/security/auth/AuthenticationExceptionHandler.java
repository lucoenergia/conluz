package org.lucoenergia.conluz.infrastructure.shared.security.auth;


import io.jsonwebtoken.JwtException;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class AuthenticationExceptionHandler {

    private final ErrorBuilder errorBuilder;
    private final MessageSource messageSource;

    public AuthenticationExceptionHandler(ErrorBuilder errorBuilder, MessageSource messageSource) {
        this.errorBuilder = errorBuilder;
        this.messageSource = messageSource;
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<RestError> handleJwtException(JwtException e) {
        return buildResponse(e);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<RestError> handleInvalidTokenException(InvalidTokenException e) {
        return buildResponse(e);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<RestError> handleAuthenticationException(AuthenticationException e) {
        return buildResponse(e);
    }

    /**
     * A failed login, already logged as a single warning by the throttling, so it is not logged again here. The
     * body is the same for both, so a disabled account cannot be told apart from a wrong password.
     */
    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<RestError> handleFailedLogin(AuthenticationException e) {
        return errorBuilder.buildWithoutLogging("error.unauthorized", List.of().toArray(), HttpStatus.UNAUTHORIZED);
    }

    /**
     * Not logged: the throttling logs once when a limit is reached, rather than once per refused attempt.
     */
    @ExceptionHandler(TooManyFailedAttemptsException.class)
    public ResponseEntity<RestError> handleTooManyFailedAttempts(TooManyFailedAttemptsException e) {
        String retryAfterSeconds = String.valueOf(e.getRetryAfterSeconds());
        String message = messageSource.getMessage(
                "error.auth.too.many.failed.attempts",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        ResponseEntity<RestError> response = errorBuilder.buildWithoutLogging(message,
                RestErrorCode.AUTH_TOO_MANY_FAILED_ATTEMPTS, Map.of("retryAfterSeconds", retryAfterSeconds),
                HttpStatus.TOO_MANY_REQUESTS);
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds)
                .body(response.getBody());
    }

    private ResponseEntity<RestError> buildResponse(Exception e) {
        return errorBuilder.build(e, "error.unauthorized", List.of().toArray(), HttpStatus.UNAUTHORIZED);
    }
}
