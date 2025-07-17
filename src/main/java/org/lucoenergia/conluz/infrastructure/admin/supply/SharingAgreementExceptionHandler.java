package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;

@RestControllerAdvice
public class SharingAgreementExceptionHandler {


    private static final Logger LOGGER = LoggerFactory.getLogger(SharingAgreementExceptionHandler.class);

    private final MessageSource messageSource;

    public SharingAgreementExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(SharingAgreementNotFoundException.class)
    public ResponseEntity<RestError> handleException(SharingAgreementNotFoundException e) {

        String sharingAgreementId = e.getId().toString();

        String message = messageSource.getMessage(
                "error.sharing.agreement.not.found",
                Collections.singletonList(sharingAgreementId).toArray(),
                LocaleContextHolder.getLocale()
        );
        LOGGER.error(message);
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
