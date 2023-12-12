package org.lucoenergia.conluz.infrastructure.shared.web.error;

import org.lucoenergia.conluz.infrastructure.shared.web.RestError;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;

@RestControllerAdvice
public class HttpMediaTypeNotSupportedExceptionHandler {

    private final MessageSource messageSource;

    public HttpMediaTypeNotSupportedExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Handle the case where no
     * {@linkplain org.springframework.http.converter.HttpMessageConverter message converters}
     * were found for PUT or POSTed content.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RestError> handleException(HttpMediaTypeNotSupportedException e) {
        String message = messageSource.getMessage(
                "error.http.media.type.not.supported",
                Collections.singletonList(e.getContentType()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
