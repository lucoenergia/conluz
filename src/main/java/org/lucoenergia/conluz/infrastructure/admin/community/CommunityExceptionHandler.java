package org.lucoenergia.conluz.infrastructure.admin.community;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;

@RestControllerAdvice
public class CommunityExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public CommunityExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(CommunityNotFoundException.class)
    public ResponseEntity<RestError> handleException(CommunityNotFoundException e) {
        String message = messageSource.getMessage(
                "error.community.not.found",
                Collections.singletonList(e.getId() != null ? e.getId().toString() : "").toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.NOT_FOUND);
    }
}
