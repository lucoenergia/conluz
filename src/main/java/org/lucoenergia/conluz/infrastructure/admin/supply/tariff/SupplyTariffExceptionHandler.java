package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyTariffNotFoundException;
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

/**
 * Exception handler for supply-tariff-related exceptions
 */
@RestControllerAdvice
public class SupplyTariffExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupplyTariffExceptionHandler.class);

    private final MessageSource messageSource;

    public SupplyTariffExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Handles SupplyTariffNotFoundException
     *
     * @param e the exception
     * @return a ResponseEntity with an error message
     */
    @ExceptionHandler(SupplyTariffNotFoundException.class)
    public ResponseEntity<RestError> handleException(SupplyTariffNotFoundException e) {
        String supplyId = e.getSupplyId().toString();

        String message = messageSource.getMessage(
                "error.supply.tariff.not.found",
                Collections.singletonList(supplyId).toArray(),
                "Supply tariff not found for supply with ID: " + supplyId,
                LocaleContextHolder.getLocale()
        );
        LOGGER.error(message);
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}