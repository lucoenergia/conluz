package org.lucoenergia.conluz.infrastructure.production.huawei;

import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiConfigIncompleteException;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiConfigNotFoundException;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiConfigPasswordRequiredException;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class HuaweiExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public HuaweiExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(HuaweiDisabledException.class)
    public ResponseEntity<RestError> handleException(HuaweiDisabledException e) {
        String message = messageSource.getMessage(
                "error.huawei.disabled",
                null,
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(HuaweiConfigPasswordRequiredException.class)
    public ResponseEntity<RestError> handleException(HuaweiConfigPasswordRequiredException e) {
        String message = messageSource.getMessage(
                "error.huawei.config.password.required",
                null,
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.HUAWEI_CONFIG_PASSWORD_REQUIRED, null,
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HuaweiConfigNotFoundException.class)
    public ResponseEntity<RestError> handleException(HuaweiConfigNotFoundException e) {
        String message = messageSource.getMessage(
                "error.huawei.config.not.found",
                new Object[]{e.getPlantId()},
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.HUAWEI_CONFIG_NOT_FOUND, null, HttpStatus.NOT_FOUND);
    }

    /**
     * A 409 rather than a 400: the body is well-formed, and what blocks the request is the stored configuration,
     * which the caller completes through the configuration write.
     */
    @ExceptionHandler(HuaweiConfigIncompleteException.class)
    public ResponseEntity<RestError> handleException(HuaweiConfigIncompleteException e) {
        String message = messageSource.getMessage(
                "error.huawei.config.incomplete",
                new Object[]{String.join(", ", e.getMissingFields())},
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.HUAWEI_CONFIG_INCOMPLETE,
                Map.of("missingFields", String.join(",", e.getMissingFields())), HttpStatus.CONFLICT);
    }
}
