package org.lucoenergia.conluz.infrastructure.shared.web.error;

import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;

@RestControllerAdvice
public class SupplyExceptionHandler {

    private final MessageSource messageSource;

    public SupplyExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(SupplyNotFoundException.class)
    public ResponseEntity<RestError> handleException(SupplyNotFoundException e) {

        String supplyId = e.getId().getId().toString();

        String message = messageSource.getMessage(
                "error.supply.not.found",
                Collections.singletonList(supplyId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
