package org.lucoenergia.conluz.infrastructure.shared.web.error;

import org.lucoenergia.conluz.infrastructure.shared.web.RestError;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class HttpMediaTypeNotAcceptableExceptionHandler {

    private final MessageSource messageSource;

    public HttpMediaTypeNotAcceptableExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<RestError> handleException(HttpMediaTypeNotAcceptableException e) {

        String message = messageSource.getMessage(
                "error.http.media.not.acceptable",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
