package org.lucoenergia.conluz.infrastructure.shared.web.error;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.lucoenergia.conluz.infrastructure.shared.web.RestError;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GenericBadRequestExceptionHandler {

    private final MessageSource messageSource;

    public GenericBadRequestExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RestError> handleException(HttpMessageNotReadableException e) {

        String message;

        if (e.getCause() instanceof UnrecognizedPropertyException unrecognizedPropertyException) {
            message = messageSource.getMessage(
                    "error.unrecognized.property",
                    List.of(unrecognizedPropertyException.getPropertyName()).toArray(),
                    LocaleContextHolder.getLocale()
            );
        } else {
            message = messageSource.getMessage(
                    "error.bad.request",
                    List.of().toArray(),
                    LocaleContextHolder.getLocale()
            );
        }

        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
