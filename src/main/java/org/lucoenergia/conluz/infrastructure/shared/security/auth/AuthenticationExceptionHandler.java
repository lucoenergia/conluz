package org.lucoenergia.conluz.infrastructure.shared.security.auth;


import io.jsonwebtoken.JwtException;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class AuthenticationExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationExceptionHandler.class);

    private final MessageSource messageSource;

    public AuthenticationExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<RestError> handleJwtException(JwtException e) {
        LOGGER.error(e.getMessage(), e);
        return buildResponse();
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<RestError> handleInvalidTokenException(InvalidTokenException e) {
        LOGGER.error(e.getMessage(), e);
        return buildResponse();
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<RestError> handleAuthenticationException(AuthenticationException e) {
        LOGGER.error(e.getMessage(), e);
        return buildResponse();
    }

    private ResponseEntity<RestError> buildResponse() {
        String message = messageSource.getMessage(
                "error.unauthorized",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );

        return new ResponseEntity<>(new RestError(HttpStatus.UNAUTHORIZED.value(), message), HttpStatus.UNAUTHORIZED);
    }
}
