package org.lucoenergia.conluz.infrastructure.shared.web.io;

import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class CsvParseExceptionHandlerTest {

    private CsvParseExceptionHandler exceptionHandler;
    private MessageSource messageSource;

    @BeforeEach
    void setup() {
        messageSource = Mockito.mock(MessageSource.class);
        exceptionHandler = new CsvParseExceptionHandler(messageSource);
        
        // Setup default message source behavior
        Mockito.when(messageSource.getMessage(
                eq("error.fields.number.does.not.match"),
                any(),
                eq(LocaleContextHolder.getLocale())
        )).thenReturn("Fields number does not match");
        
        Mockito.when(messageSource.getMessage(
                eq("error.supply.unable.to.parse.file"),
                any(),
                eq(LocaleContextHolder.getLocale())
        )).thenReturn("Unable to parse file");
        
        Mockito.when(messageSource.getMessage(
                eq("error.bad.request"),
                any(),
                eq(LocaleContextHolder.getLocale())
        )).thenReturn("Bad request");
    }

    @Test
    void testHandleCsvParsingErrorWithCsvRequiredFieldEmptyException() {
        // Arrange
        CsvRequiredFieldEmptyException csvException = new CsvRequiredFieldEmptyException();
        Exception exception = new Exception("Wrapper exception", csvException);

        // Act
        ResponseEntity<RestError> response = exceptionHandler.handleCsvParsingError(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("Fields number does not match", response.getBody().getMessage());
        
        // Verify message source was called with correct parameters
        Mockito.verify(messageSource).getMessage(
                eq("error.fields.number.does.not.match"),
                any(),
                eq(LocaleContextHolder.getLocale())
        );
    }

    @Test
    void testHandleCsvParsingErrorWithCsvDataTypeMismatchException() {
        // Arrange
        CsvDataTypeMismatchException csvException = new CsvDataTypeMismatchException();
        Exception exception = new Exception("Wrapper exception", csvException);

        // Act
        ResponseEntity<RestError> response = exceptionHandler.handleCsvParsingError(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("Unable to parse file", response.getBody().getMessage());
        
        // Verify message source was called with correct parameters
        Mockito.verify(messageSource).getMessage(
                eq("error.supply.unable.to.parse.file"),
                any(),
                eq(LocaleContextHolder.getLocale())
        );
    }

    @Test
    void testHandleCsvParsingErrorWithGenericException() {
        // Arrange
        Exception exception = new Exception("Generic exception");

        // Act
        ResponseEntity<RestError> response = exceptionHandler.handleCsvParsingError(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("Bad request", response.getBody().getMessage());
        
        // Verify message source was called with correct parameters
        Mockito.verify(messageSource).getMessage(
                eq("error.bad.request"),
                any(),
                eq(LocaleContextHolder.getLocale())
        );
    }
}