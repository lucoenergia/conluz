package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.UserAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.create.DefaultAdminUserAlreadyInitializedException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
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
    private final ErrorBuilder errorBuilder;

    public UserExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(DefaultAdminUserAlreadyInitializedException.class)
    public ResponseEntity<RestError> handleException(DefaultAdminUserAlreadyInitializedException e) {

        String message = messageSource.getMessage(
                "error.admin.user.already.initialized",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.FORBIDDEN);
    }

    /**
     * A duplicate personal ID is a conflict with existing state, answered 409 like the other
     * "already exists" errors. Neither the message nor the params carry the personal ID, so the
     * response cannot be used to confirm which personal IDs are registered.
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<RestError> handleException(UserAlreadyExistsException e) {

        String message = messageSource.getMessage(
                "error.user.already.exists",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.USER_ALREADY_EXISTS, null, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<RestError> handleException(UserNotFoundException e) {

        String message = e.isLookedUpByPersonalId()
                ? messageSource.getMessage(
                        "error.user.not.found.by.personal.id",
                        List.of().toArray(),
                        LocaleContextHolder.getLocale())
                : messageSource.getMessage(
                        "error.user.not.found",
                        List.of(e.getId()).toArray(),
                        LocaleContextHolder.getLocale()
                );
        return errorBuilder.build(message, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(LastPlatformAdminException.class)
    public ResponseEntity<RestError> handleException(LastPlatformAdminException e) {

        String message = messageSource.getMessage(
                "error.user.last.platform.admin",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.USER_LAST_PLATFORM_ADMIN, null, HttpStatus.CONFLICT);
    }
}
