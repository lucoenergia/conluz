package org.lucoenergia.conluz.infrastructure.shared.web.io;

import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class CsvParseExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsvParseExceptionHandler.class);

    private final MessageSource messageSource;

    public CsvParseExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public ResponseEntity<RestError> handleCsvParsingError(Exception ex) {
        final HttpStatus status = HttpStatus.BAD_REQUEST;

        if (ex.getCause() instanceof CsvRequiredFieldEmptyException) {
            LOGGER.error("Error parsing file", ex.getCause());
            return createErrorResponse("error.fields.number.does.not.match", status);
        }
        if (ex.getCause() instanceof CsvDataTypeMismatchException) {
            LOGGER.error("Error parsing line", ex.getCause());
            return createErrorResponse("error.supply.unable.to.parse.file", status);
        }

        LOGGER.error("Error processing file", ex);
        return createErrorResponse("error.bad.request", status);
    }

    private ResponseEntity<RestError> createErrorResponse(String messageKey, HttpStatus status) {
        String message = messageSource.getMessage(messageKey,
                Collections.emptyList().toArray(),
                LocaleContextHolder.getLocale());
        return new ResponseEntity<>(
                new RestError(status.value(), message),
                status
        );
    }
}
