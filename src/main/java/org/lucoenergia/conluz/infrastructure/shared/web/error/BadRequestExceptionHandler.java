package org.lucoenergia.conluz.infrastructure.shared.web.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.*;

@RestControllerAdvice
public class BadRequestExceptionHandler {

    private final MessageSource messageSource;

    public BadRequestExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
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

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<RestError> handleException(MissingPathVariableException e) {

        String message = messageSource.getMessage(
                "error.missing.path.variable",
                List.of().toArray(),
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestError> handleException(MethodArgumentNotValidException e) {

        List<String> errorMessages = new ArrayList<>();
        for (FieldError error : e.getFieldErrors()) {
            errorMessages.add(messageSource.getMessage(
                    "error.property.invalid",
                    Arrays.asList(error.getField(), error.getRejectedValue(), error.getDefaultMessage()).toArray(),
                    LocaleContextHolder.getLocale()
            ));
        }

        String message = messageSource.getMessage(
                "error.properties.invalid.with.reason",
                List.of(String.join("", errorMessages)).toArray(),
                LocaleContextHolder.getLocale()
        );

        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RestError> handleException(HttpMediaTypeNotSupportedException e) {
        String message = messageSource.getMessage(
                "error.http.media.type.not.supported",
                Collections.singletonList(e.getContentType()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
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

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<RestError> handleException(PropertyReferenceException e) {

        String message = messageSource.getMessage(
                "error.property.not.found",
                List.of(e.getPropertyName()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RestError> handleException(ConstraintViolationException e) {

        String message = e.getConstraintViolations()
                .stream()
                .map(error -> messageSource.getMessage(
                        "error.property.invalid",
                        List.of(error.getPropertyPath(), error.getInvalidValue(), error.getMessage()).toArray(),
                        LocaleContextHolder.getLocale()
                ))
                .reduce(messageSource.getMessage(
                        "error.properties.invalid",
                        List.of().toArray(),
                        LocaleContextHolder.getLocale()
                ), String::concat);

        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RestError> handleException(HttpMessageNotReadableException e) {

        String message = null;

        if (e.getCause() instanceof UnrecognizedPropertyException unrecognizedPropertyException) {
            message = messageSource.getMessage(
                    "error.unrecognized.property",
                    List.of(unrecognizedPropertyException.getPropertyName()).toArray(),
                    LocaleContextHolder.getLocale()
            );
        } else if (e.getCause() instanceof InvalidFormatException invalidValueException) {
            Optional<String> fieldName = invalidValueException.getPath() != null &&
                    !invalidValueException.getPath().isEmpty() ?
                    Optional.of(invalidValueException.getPath().get(0).getFieldName()) : Optional.empty();

            if (fieldName.isPresent()) {
                message = messageSource.getMessage(
                        "error.property.invalid.format",
                        List.of(fieldName.orElse(""), invalidValueException.getValue()).toArray(),
                        LocaleContextHolder.getLocale()
                );
            }
        }
        if (message == null) {
            message = messageSource.getMessage(
                    "error.bad.request",
                    List.of().toArray(),
                    LocaleContextHolder.getLocale()
            );
        }

        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RestError> handleException(HttpRequestMethodNotSupportedException e) {

        String message = messageSource.getMessage(
                "error.http.request.method.not.supported",
                List.of(e.getMethod()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RestError> handleException(IllegalArgumentException e) {
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), e.getMessage()),
                HttpStatus.BAD_REQUEST);
    }
}
