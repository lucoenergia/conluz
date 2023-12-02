package org.lucoenergia.conluz.infrastructure.shared.web.error;

import org.lucoenergia.conluz.infrastructure.shared.web.RestError;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.Arrays;

@RestControllerAdvice
public class MethodArgumentTypeMismatchExceptionHandler {

    private final MessageSource messageSource;

    public MethodArgumentTypeMismatchExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RestError> handleException(MethodArgumentTypeMismatchException e) {

        String argumentName = e.getName();
        Object currentValue = e.getValue();
        Class<?> expectedType = e.getRequiredType();
        String expectedFormat = "";

        if (OffsetDateTime.class.equals(expectedType)) {
            expectedFormat = "yyyy-mm-ddThh:mm:ss.000+h:mm";
        }

        String message = messageSource.getMessage(
                "error.method.argument.type.mismatch",
                Arrays.asList(argumentName, currentValue, expectedFormat).toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
