package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientNotFoundException;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
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

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public SupplyExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(SupplyNotFoundException.class)
    public ResponseEntity<RestError> handleException(SupplyNotFoundException e) {

        String identifier;
        if (e.getId() != null) {
            identifier = e.getId().getId().toString();
        } else {
            identifier = e.getCode().getCode();
        }

        String message = messageSource.getMessage(
                "error.supply.not.found",
                Collections.singletonList(identifier).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SupplyPartitionCoefficientNotFoundException.class)
    public ResponseEntity<RestError> handleException(SupplyPartitionCoefficientNotFoundException e) {
        return errorBuilder.build(e.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SupplyAlreadyExistsException.class)
    public ResponseEntity<RestError> handleException(SupplyAlreadyExistsException e) {

        String message = messageSource.getMessage(
                "error.supply.already.exists",
                List.of(e.getCode().getCode()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }
}
