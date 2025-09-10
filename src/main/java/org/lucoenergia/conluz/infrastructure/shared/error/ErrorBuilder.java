package org.lucoenergia.conluz.infrastructure.shared.error;

import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ErrorBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorBuilder.class);

    private final MessageSource messageSource;

    public ErrorBuilder(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public ResponseEntity<RestError> build(Throwable exception, String code, Object[] args, HttpStatus status) {

        final String traceId = UUID.randomUUID().toString();

        LOGGER.error("Error: {}. Trace ID: {}.", exception.getMessage(), traceId, exception);

        final String message = messageSource.getMessage(
                code,
                args,
                LocaleContextHolder.getLocale()
        );

        return new ResponseEntity<>(new RestError(status.value(), message, traceId), status);
    }

    public ResponseEntity<RestError> build(String exceptionMessage, String code, Object[] args, HttpStatus status) {

        final String traceId = UUID.randomUUID().toString();

        LOGGER.error("Error message: {}. Trace ID: {}.", exceptionMessage, traceId);

        final String message = messageSource.getMessage(
                code,
                args,
                LocaleContextHolder.getLocale()
        );

        return new ResponseEntity<>(new RestError(status.value(), message, traceId), status);
    }

    public ResponseEntity<RestError> build(String message, HttpStatus status) {

        final String traceId = UUID.randomUUID().toString();

        LOGGER.error("Error message: {}. Trace ID: {}.", message, traceId);

        return new ResponseEntity<>(new RestError(status.value(), message, traceId), status);
    }
}
