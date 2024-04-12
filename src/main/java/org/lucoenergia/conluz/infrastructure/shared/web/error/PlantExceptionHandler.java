package org.lucoenergia.conluz.infrastructure.shared.web.error;

import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.PlantAlreadyExistsException;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;

@RestControllerAdvice
public class PlantExceptionHandler {

    private final MessageSource messageSource;

    public PlantExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(PlantAlreadyExistsException.class)
    public ResponseEntity<RestError> handleException(PlantAlreadyExistsException e) {

        String plantCode = e.getCode().toString();

        String message = messageSource.getMessage(
                "error.plant.already.exists",
                Collections.singletonList(plantCode).toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PlantNotFoundException.class)
    public ResponseEntity<RestError> handleException(PlantNotFoundException e) {

        String plantId = e.getId().toString();

        String message = messageSource.getMessage(
                "error.plant.not.found",
                Collections.singletonList(plantId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
