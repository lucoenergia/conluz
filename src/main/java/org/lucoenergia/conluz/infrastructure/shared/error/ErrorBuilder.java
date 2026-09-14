package org.lucoenergia.conluz.infrastructure.shared.error;

import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ErrorBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorBuilder.class);

    private final MessageSource messageSource;

    public ErrorBuilder(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public ResponseEntity<RestError> build(Throwable exception, String messageKey, Object[] args, HttpStatus status) {

        final String traceId = UUID.randomUUID().toString();

        LOGGER.error("Error: {}. Trace ID: {}.", exception.getMessage(), traceId, exception);

        final String message = messageSource.getMessage(
                messageKey,
                args,
                LocaleContextHolder.getLocale()
        );

        return new ResponseEntity<>(new RestError(status.value(), message, traceId), status);
    }

    public ResponseEntity<RestError> build(String exceptionMessage, String messageKey, Object[] args, HttpStatus status) {

        final String traceId = UUID.randomUUID().toString();

        LOGGER.error("Error message: {}. Trace ID: {}.", exceptionMessage, traceId);

        final String message = messageSource.getMessage(
                messageKey,
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

    public ResponseEntity<RestError> build(String message, RestErrorCode code, Map<String, String> params,
                                            HttpStatus status) {

        final String traceId = UUID.randomUUID().toString();

        LOGGER.error("Error message: {}. Trace ID: {}.", message, traceId);

        final RestError restError = new RestError(status.value(), message, traceId,
                List.of(new RestErrorDetail(message, code, params)));

        return new ResponseEntity<>(restError, status);
    }

    /**
     * Builds one {@link RestError} from a pre-built collection of {@link RestErrorDetail}, e.g. the
     * per-line errors found while validating an uploaded file. {@code message} is the short summary
     * shown in {@link RestError#getMessage()} -- never a concatenation of the individual details.
     */
    public ResponseEntity<RestError> build(String message, List<RestErrorDetail> errors, HttpStatus status) {

        final String traceId = UUID.randomUUID().toString();

        LOGGER.error("Error message: {}. Trace ID: {}. {} error(s).", message, traceId, errors.size());

        final RestError restError = new RestError(status.value(), message, traceId, errors);

        return new ResponseEntity<>(restError, status);
    }
}
