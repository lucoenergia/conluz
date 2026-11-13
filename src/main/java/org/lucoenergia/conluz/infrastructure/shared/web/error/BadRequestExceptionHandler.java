package org.lucoenergia.conluz.infrastructure.shared.web.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
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
    private final ErrorBuilder errorBuilder;

    public BadRequestExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RestError> handleException(MissingServletRequestParameterException e) {

        String parameterName = e.getParameterName();

        String message = messageSource.getMessage(
                "error.missing.parameter",
                List.of(parameterName).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<RestError> handleException(MissingPathVariableException e) {

        String message = messageSource.getMessage(
                "error.missing.path.variable",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
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
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestError> handleException(MethodArgumentNotValidException e) {

        if (e.getFieldErrors().stream().anyMatch(error -> maxLengthOf(error).isPresent())) {
            return fieldErrorsWithCodes(e);
        }

        List<String> errorMessages = new ArrayList<>();
        // The rejected value is never repeated: it may be a password, an email address or a personal ID (#479)
        for (FieldError error : e.getFieldErrors()) {
            errorMessages.add(invalidPropertyMessage(error));
        }

        String message = messageSource.getMessage(
                "error.properties.invalid.with.reason",
                List.of(String.join("", errorMessages)).toArray(),
                LocaleContextHolder.getLocale()
        );

        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }

    /**
     * One detail per field error, in the order of the fields: {@code FIELD_TOO_LONG} with its parameters for a value
     * longer than its {@code @Size}, and today's untyped detail for any other error (#574). The rejected value is only
     * measured, never repeated.
     */
    private ResponseEntity<RestError> fieldErrorsWithCodes(MethodArgumentNotValidException e) {
        List<RestErrorDetail> details = e.getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> maxLengthOf(error)
                        .map(maxLength -> tooLong(error, maxLength))
                        .orElseGet(() -> new RestErrorDetail(invalidPropertyMessage(error), null, null)))
                .toList();

        String message = details.size() == 1
                ? details.get(0).getMessage()
                : messageSource.getMessage("error.properties.invalid.count", new Object[]{details.size()},
                LocaleContextHolder.getLocale());

        return errorBuilder.build(message, details, HttpStatus.BAD_REQUEST);
    }

    private RestErrorDetail tooLong(FieldError error, int maxLength) {
        String field = error.getField();
        String length = String.valueOf(((CharSequence) error.getRejectedValue()).length());
        String max = String.valueOf(maxLength);
        String message = messageSource.getMessage("error.property.too.long", new Object[]{field, max, length},
                LocaleContextHolder.getLocale());
        return new RestErrorDetail(message, RestErrorCode.FIELD_TOO_LONG,
                Map.of("field", field, "maxLength", max, "n", length));
    }

    /**
     * The maximum of the {@code @Size} the error breaks, if the rejected value is a text longer than it. Only
     * {@code @Size} declares a maximum length, since it is the constraint the contract publishes as
     * {@code maxLength}; {@code @Length} is used where a {@code maxLength} must not be published (#574).
     */
    private static Optional<Integer> maxLengthOf(FieldError error) {
        if (!error.contains(ConstraintViolation.class)
                || !(error.getRejectedValue() instanceof CharSequence value)) {
            return Optional.empty();
        }
        ConstraintDescriptor<?> descriptor = error.unwrap(ConstraintViolation.class).getConstraintDescriptor();
        if (!(descriptor.getAnnotation() instanceof Size size) || value.length() <= size.max()) {
            return Optional.empty();
        }
        return Optional.of(size.max());
    }

    private String invalidPropertyMessage(FieldError error) {
        return messageSource.getMessage(
                "error.property.invalid",
                Arrays.asList(error.getField(), error.getDefaultMessage()).toArray(),
                LocaleContextHolder.getLocale()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RestError> handleException(HttpMediaTypeNotSupportedException e) {
        String message = messageSource.getMessage(
                "error.http.media.type.not.supported",
                Collections.singletonList(e.getContentType()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<RestError> handleException(HttpMediaTypeNotAcceptableException e) {

        String message = messageSource.getMessage(
                "error.http.media.not.acceptable",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<RestError> handleException(PropertyReferenceException e) {

        String message = messageSource.getMessage(
                "error.property.not.found",
                List.of(e.getPropertyName()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RestError> handleException(ConstraintViolationException e) {

        String message = e.getConstraintViolations()
                .stream()
                .map(error -> messageSource.getMessage(
                        "error.property.invalid",
                        // Without the invalid value, as for a body above
                        List.of(error.getPropertyPath(), error.getMessage()).toArray(),
                        LocaleContextHolder.getLocale()
                ))
                .reduce(messageSource.getMessage(
                        "error.properties.invalid",
                        List.of().toArray(),
                        LocaleContextHolder.getLocale()
                ), String::concat);

        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
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
        } else if (e.getCause() instanceof InvalidNullException invalidNullException) {
            message = messageSource.getMessage(
                    "error.property.null",
                    List.of(invalidNullException.getPropertyName().getSimpleName()).toArray(),
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

        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RestError> handleException(HttpRequestMethodNotSupportedException e) {

        String message = messageSource.getMessage(
                "error.http.request.method.not.supported",
                List.of(e.getMethod()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Logged at ERROR with the stack trace although it answers 400: in this application an
     * {@link IllegalArgumentException} is thrown for a broken invariant, a programming error, not for a refusal of
     * the request's data, which has its own exception (#479).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RestError> handleException(IllegalArgumentException e) {
        return errorBuilder.buildForBrokenInvariant(e, "error.invalid.arguments", List.of().toArray(), HttpStatus.BAD_REQUEST);
    }
}
