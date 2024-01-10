package org.lucoenergia.conluz.infrastructure.shared.web.error;

import org.lucoenergia.conluz.domain.admin.user.UserAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.user.create.DefaultAdminUserAlreadyInitializedException;
import org.lucoenergia.conluz.infrastructure.shared.web.RestError;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class UserExceptionHandler {

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
        return new ResponseEntity<>(new RestError(HttpStatus.FORBIDDEN.value(), message), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<RestError> handleException(UserAlreadyExistsException e) {

        String message = messageSource.getMessage(
                "error.user.already.exists",
                List.of(e.getUserId().getPersonalId()).toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
