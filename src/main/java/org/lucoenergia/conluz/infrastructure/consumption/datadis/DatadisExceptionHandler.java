package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DatadisExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public DatadisExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(DatadisDisabledException.class)
    public ResponseEntity<RestError> handleException(DatadisDisabledException e) {
        String message = messageSource.getMessage(
                "error.datadis.disabled",
                null,
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.CONFLICT);
    }
}
