package org.lucoenergia.conluz.infrastructure.production.plant;

import org.lucoenergia.conluz.domain.production.plant.PlantAlreadyExistsException;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
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
public class PlantExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public PlantExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(PlantAlreadyExistsException.class)
    public ResponseEntity<RestError> handleException(PlantAlreadyExistsException e) {

        String plantCode = e.getCode().toString();

        String message = messageSource.getMessage(
                "error.plant.already.exists",
                Collections.singletonList(plantCode).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PlantNotFoundException.class)
    public ResponseEntity<RestError> handleException(PlantNotFoundException e) {

        String plantId = e.getId().toString();

        String message = messageSource.getMessage(
                "error.plant.not.found",
                Collections.singletonList(plantId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.NOT_FOUND);
    }
}
