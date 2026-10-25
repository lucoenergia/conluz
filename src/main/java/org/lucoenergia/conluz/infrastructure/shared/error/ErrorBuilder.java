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

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the {@link RestError} body of every refused or failed request, and logs it at the level its status calls
 * for: ERROR, with the stack trace, for a 5xx; WARN, one line, for a security refusal (401, 403, 429); and INFO, one
 * line, for any other refusal of the request's data or of a business rule. Below 500 nothing that may carry a value
 * from the request is logged with a stack trace.
 */
@Component
public class ErrorBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorBuilder.class);

    private final MessageSource messageSource;
    private final Clock clock;

    public ErrorBuilder(MessageSource messageSource, Clock clock) {
        this.messageSource = messageSource;
        this.clock = clock;
    }

    public ResponseEntity<RestError> build(Throwable exception, String messageKey, Object[] args, HttpStatus status) {

        final String traceId = UUID.randomUUID().toString();

        if (status.is5xxServerError()) {
            LOGGER.error("Error: {}. Trace ID: {}.", exception.getMessage(), traceId, exception);
        } else {
            // Only the type: below 500 the message may carry a value of the request, or part of a token
            log(status, exception.getClass().getSimpleName(), traceId);
        }

        final String message = messageSource.getMessage(
                messageKey,
                args,
                LocaleContextHolder.getLocale()
        );

        return new ResponseEntity<>(new RestError(now(), status.value(), message, traceId), status);
    }

    public ResponseEntity<RestError> build(String exceptionMessage, String messageKey, Object[] args, HttpStatus status) {

        final String traceId = UUID.randomUUID().toString();

        log(status, exceptionMessage, traceId);

        final String message = messageSource.getMessage(
                messageKey,
                args,
                LocaleContextHolder.getLocale()
        );

        return new ResponseEntity<>(new RestError(now(), status.value(), message, traceId), status);
    }

    public ResponseEntity<RestError> build(String message, HttpStatus status) {

        final String traceId = UUID.randomUUID().toString();

        log(status, message, traceId);

        return new ResponseEntity<>(new RestError(now(), status.value(), message, traceId), status);
    }

    public ResponseEntity<RestError> build(String message, RestErrorCode code, Map<String, String> params,
                                            HttpStatus status) {

        final String traceId = UUID.randomUUID().toString();

        log(status, message, traceId);

        final RestError restError = new RestError(now(), status.value(), message, traceId,
                List.of(new RestErrorDetail(message, code, params)));

        return new ResponseEntity<>(restError, status);
    }

    /**
     * Same body as {@link #build(Throwable, String, Object[], HttpStatus)}, but logged at ERROR with the stack trace
     * whatever the status. For an exception that, in this application, means a broken invariant -- a programming
     * error -- even though the request is answered with a 4xx, so that it stays visible among the real failures.
     */
    public ResponseEntity<RestError> buildForBrokenInvariant(Throwable exception, String messageKey, Object[] args,
                                                             HttpStatus status) {

        final String traceId = UUID.randomUUID().toString();

        LOGGER.error("Error: {}. Trace ID: {}.", exception.getMessage(), traceId, exception);

        final String message = messageSource.getMessage(
                messageKey,
                args,
                LocaleContextHolder.getLocale()
        );

        return new ResponseEntity<>(new RestError(now(), status.value(), message, traceId), status);
    }

    /**
     * Same body as {@link #build(Throwable, String, Object[], HttpStatus)}, but nothing is logged. For failures
     * that are already logged where they happen, in a form that suits them better than an error with a stack
     * trace, such as a failed authentication attempt.
     */
    public ResponseEntity<RestError> buildWithoutLogging(String messageKey, Object[] args, HttpStatus status) {

        final String message = messageSource.getMessage(
                messageKey,
                args,
                LocaleContextHolder.getLocale()
        );

        return new ResponseEntity<>(new RestError(now(), status.value(), message, UUID.randomUUID().toString()),
                status);
    }

    /**
     * Same body as {@link #build(String, RestErrorCode, Map, HttpStatus)}, but nothing is logged. For failures
     * that are already logged where they happen, such as a failed authentication attempt.
     */
    public ResponseEntity<RestError> buildWithoutLogging(String message, RestErrorCode code,
                                                         Map<String, String> params, HttpStatus status) {

        final RestError restError = new RestError(now(), status.value(), message, UUID.randomUUID().toString(),
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

        log(status, message + " (" + errors.size() + " error(s))", traceId);

        final RestError restError = new RestError(now(), status.value(), message, traceId, errors);

        return new ResponseEntity<>(restError, status);
    }

    /**
     * One line, without a stack trace, at the level of the status: see the class comment.
     */
    private static void log(HttpStatus status, String message, String traceId) {
        if (status.is5xxServerError()) {
            LOGGER.error("Error message: {}. Trace ID: {}.", message, traceId);
        } else if (isSecurityRefusal(status)) {
            LOGGER.warn("Request refused: {}. Trace ID: {}.", message, traceId);
        } else {
            LOGGER.info("Request refused: {}. Trace ID: {}.", message, traceId);
        }
    }

    private static boolean isSecurityRefusal(HttpStatus status) {
        return status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN
                || status == HttpStatus.TOO_MANY_REQUESTS;
    }

    /**
     * The instant of the error, with the offset of the JVM's default zone.
     */
    private OffsetDateTime now() {
        return OffsetDateTime.now(clock.withZone(ZoneId.systemDefault()));
    }
}
