package org.lucoenergia.conluz.infrastructure.shared.web.error;

import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.Map;

/**
 * An upload over the multipart limits, answered 413 with the file size limit, the one a client has to respect: the
 * request limit only adds room for the rest of the form.
 */
@RestControllerAdvice
public class PayloadTooLargeExceptionHandler {

    private final ErrorBuilder errorBuilder;
    private final MessageSource messageSource;
    private final MultipartProperties multipartProperties;

    public PayloadTooLargeExceptionHandler(ErrorBuilder errorBuilder, MessageSource messageSource,
                                           MultipartProperties multipartProperties) {
        this.errorBuilder = errorBuilder;
        this.messageSource = messageSource;
        this.multipartProperties = multipartProperties;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<RestError> handleException(MaxUploadSizeExceededException e) {
        String message = messageSource.getMessage(
                "error.request.payload.too.large",
                List.of(String.valueOf(multipartProperties.getMaxFileSize().toMegabytes())).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.REQUEST_PAYLOAD_TOO_LARGE,
                Map.of("maxFileSizeBytes", String.valueOf(multipartProperties.getMaxFileSize().toBytes())),
                HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
