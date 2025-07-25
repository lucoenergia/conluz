package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.UserAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.create.DefaultAdminUserAlreadyInitializedException;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class UserExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserExceptionHandler.class);

    private final MessageSource messageSource;

    public UserExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(DefaultAdminUserAlreadyInitializedException.class)
    public ResponseEntity<RestError> handleException(DefaultAdminUserAlreadyInitializedException e) {

        String message = messageSource.getMessage(
                "error.admin.user.already.initialized",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        LOGGER.error(message);
        return new ResponseEntity<>(new RestError(HttpStatus.FORBIDDEN.value(), message), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<RestError> handleException(UserAlreadyExistsException e) {

        String message = messageSource.getMessage(
                "error.user.already.exists",
                List.of(e.getUserId().getPersonalId()).toArray(),
                LocaleContextHolder.getLocale()
        );
        LOGGER.error(message);
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<RestError> handleException(UserNotFoundException e) {

        String message = messageSource.getMessage(
                "error.user.not.found",
                List.of(e.getId()).toArray(),
                LocaleContextHolder.getLocale()
        );
        LOGGER.error(message);
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
