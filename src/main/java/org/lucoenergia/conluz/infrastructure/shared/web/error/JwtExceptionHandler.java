package org.lucoenergia.conluz.infrastructure.shared.web.error;


import io.jsonwebtoken.JwtException;
import org.lucoenergia.conluz.infrastructure.shared.web.RestError;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class JwtExceptionHandler {

    private final MessageSource messageSource;

    public JwtExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<RestError> handleException(JwtException e) {
        String message = messageSource.getMessage(
                "error.unauthorized",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.UNAUTHORIZED.value(), message), HttpStatus.UNAUTHORIZED);
    }
}
