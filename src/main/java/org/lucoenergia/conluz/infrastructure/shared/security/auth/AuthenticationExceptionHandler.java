package org.lucoenergia.conluz.infrastructure.shared.security.auth;


import io.jsonwebtoken.JwtException;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class AuthenticationExceptionHandler {

    private final ErrorBuilder errorBuilder;

    public AuthenticationExceptionHandler(ErrorBuilder errorBuilder) {
        this.errorBuilder = errorBuilder;
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

    private ResponseEntity<RestError> buildResponse(Exception e) {
        return errorBuilder.build(e, "error.unauthorized", List.of().toArray(), HttpStatus.UNAUTHORIZED);
    }
}
