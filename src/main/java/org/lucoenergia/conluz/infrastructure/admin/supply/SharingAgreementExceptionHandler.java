package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException;
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
public class SharingAgreementExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public SharingAgreementExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(SharingAgreementNotFoundException.class)
    public ResponseEntity<RestError> handleException(SharingAgreementNotFoundException e) {

        String sharingAgreementId = e.getId().toString();

        String message = messageSource.getMessage(
                "error.sharing.agreement.not.found",
                Collections.singletonList(sharingAgreementId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }
}
