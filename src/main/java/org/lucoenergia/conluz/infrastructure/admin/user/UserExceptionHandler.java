package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.UserAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.create.DefaultAdminUserAlreadyInitializedException;
import org.lucoenergia.conluz.domain.admin.user.password.IncorrectCurrentPasswordException;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicyViolationException;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordUnchangedException;
import org.lucoenergia.conluz.domain.admin.user.password.reset.PasswordResetTokenInvalidException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.infrastructure.admin.user.password.PasswordPolicyMessages;
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
import java.util.Map;

@RestControllerAdvice
public class UserExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;
    private final PasswordPolicyMessages passwordPolicyMessages;

    public UserExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder,
                                PasswordPolicyMessages passwordPolicyMessages) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
        this.passwordPolicyMessages = passwordPolicyMessages;
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

        String message = messageSource.getMessage(
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

    /**
     * Neither the message nor the params carry the rejected password: only the rule that failed.
     */
    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<RestError> handleException(PasswordPolicyViolationException e) {

        String message = passwordPolicyMessages.messageFor(e.getRule());
        return errorBuilder.build(message, RestErrorCode.USER_PASSWORD_POLICY_VIOLATION,
                Map.of("rule", e.getRule().name()), HttpStatus.BAD_REQUEST);
    }

    /**
     * A 400, never a 401: the caller's session is valid, and clients treat a 401 as a session that has ended.
     * Not logged here: the throttling already logs every wrong current password as a single warning.
     */
    @ExceptionHandler(IncorrectCurrentPasswordException.class)
    public ResponseEntity<RestError> handleException(IncorrectCurrentPasswordException e) {

        String message = messageSource.getMessage(
                "error.user.current.password.incorrect",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.buildWithoutLogging(message, RestErrorCode.USER_CURRENT_PASSWORD_INCORRECT, null,
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Not logged: the caller's current password was right, and the refusal is an ordinary input error.
     */
    @ExceptionHandler(PasswordUnchangedException.class)
    public ResponseEntity<RestError> handleException(PasswordUnchangedException e) {

        String message = messageSource.getMessage(
                "error.user.password.unchanged",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.buildWithoutLogging(message, RestErrorCode.USER_PASSWORD_UNCHANGED, null,
                HttpStatus.BAD_REQUEST);
    }

    /**
     * The same answer whatever made the token unusable. Not logged here: the throttling logs each invalid token
     * once, with the client address.
     */
    @ExceptionHandler(PasswordResetTokenInvalidException.class)
    public ResponseEntity<RestError> handleException(PasswordResetTokenInvalidException e) {

        String message = messageSource.getMessage(
                "error.user.password.reset.token.invalid",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.buildWithoutLogging(message, RestErrorCode.USER_PASSWORD_RESET_TOKEN_INVALID, null,
                HttpStatus.BAD_REQUEST);
    }
}
