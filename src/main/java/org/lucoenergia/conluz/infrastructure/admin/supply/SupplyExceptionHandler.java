package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.UserAlreadyExistsException;
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
import java.util.List;

@RestControllerAdvice
public class SupplyExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupplyExceptionHandler.class);

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
        LOGGER.error(message);
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SupplyAlreadyExistsException.class)
    public ResponseEntity<RestError> handleException(SupplyAlreadyExistsException e) {

        String message = messageSource.getMessage(
                "error.supply.already.exists",
                List.of(e.getCode().getCode()).toArray(),
                LocaleContextHolder.getLocale()
        );
        LOGGER.error(message);
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
