package org.lucoenergia.conluz.infrastructure.shared.web;

import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RestControllerAdvice
public class GlobalRestExceptionHandler {

    private final MessageSource messageSource;

    public GlobalRestExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestError> handleException(MethodArgumentNotValidException e) {

        String message = e.getFieldErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .reduce("Errors found:", String::concat);
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RestError> handleException(MissingServletRequestParameterException e) {

        String parameterName = e.getParameterName();

        String message = messageSource.getMessage(
                "error.missing.parameter",
                List.of(parameterName).toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
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

    @ExceptionHandler(SupplyNotFoundException.class)
    public ResponseEntity<RestError> handleException(SupplyNotFoundException e) {

        String supplyId = e.getId().getId();

        String message = messageSource.getMessage(
                "error.supply.not.found",
                Collections.singletonList(supplyId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
