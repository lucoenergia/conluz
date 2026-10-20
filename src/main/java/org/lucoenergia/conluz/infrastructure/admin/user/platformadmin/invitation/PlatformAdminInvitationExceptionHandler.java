package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCodeAttemptsExhaustedException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCodeInvalidException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminAlreadyInvitedException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminInvitationLoginRequiredException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminInvitationResendLimitException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminInvitationUnavailableException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminNotInvitedException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.UserAlreadyPlatformAdminException;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * The answers of inviting a person to the platform-admin role and of accepting such an invitation (ADR-0004). No
 * message or parameter carries a personal id, an email address, a token or a confirmation code: only user ids.
 */
@RestControllerAdvice
public class PlatformAdminInvitationExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public PlatformAdminInvitationExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(UserAlreadyPlatformAdminException.class)
    public ResponseEntity<RestError> handleException(UserAlreadyPlatformAdminException e) {
        String userId = e.getUserId().toString();
        return errorBuilder.build(message("error.user.already.platform.admin", userId),
                RestErrorCode.USER_ALREADY_PLATFORM_ADMIN, Map.of("userId", userId), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PlatformAdminAlreadyInvitedException.class)
    public ResponseEntity<RestError> handleException(PlatformAdminAlreadyInvitedException e) {
        String userId = e.getUserId().toString();
        return errorBuilder.build(message("error.platform.admin.already.invited", userId),
                RestErrorCode.PLATFORM_ADMIN_ALREADY_INVITED, Map.of("userId", userId), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PlatformAdminNotInvitedException.class)
    public ResponseEntity<RestError> handleException(PlatformAdminNotInvitedException e) {
        String userId = e.getUserId().toString();
        return errorBuilder.build(message("error.platform.admin.not.invited", userId),
                RestErrorCode.PLATFORM_ADMIN_NOT_INVITED, Map.of("userId", userId), HttpStatus.CONFLICT);
    }

    /**
     * A 429 with a {@code Retry-After} header, as the resend of a community invitation. An expected answer, so it is
     * not logged.
     */
    @ExceptionHandler(PlatformAdminInvitationResendLimitException.class)
    public ResponseEntity<RestError> handleException(PlatformAdminInvitationResendLimitException e) {
        String userId = e.getUserId().toString();
        String retryAfterSeconds = String.valueOf(e.getRetryAfterSeconds());
        ResponseEntity<RestError> response = errorBuilder.buildWithoutLogging(
                message("error.platform.admin.invitation.resend.limit", userId, retryAfterSeconds),
                RestErrorCode.PLATFORM_ADMIN_INVITATION_RESEND_LIMIT_REACHED,
                Map.of("userId", userId, "retryAfterSeconds", retryAfterSeconds), HttpStatus.TOO_MANY_REQUESTS);
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds)
                .body(response.getBody());
    }

    /**
     * A 503 before anything is looked up or written. The warning naming the email category is logged where the
     * refusal is decided, so it is not logged again here.
     */
    @ExceptionHandler(PlatformAdminInvitationUnavailableException.class)
    public ResponseEntity<RestError> handleException(PlatformAdminInvitationUnavailableException e) {
        return errorBuilder.buildWithoutLogging(message("error.platform.admin.invitation.unavailable"),
                RestErrorCode.PLATFORM_ADMIN_INVITATION_UNAVAILABLE, Map.of(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * A 409: the account has a password, so it accepts after logging in. An expected answer that the client turns
     * into a login, so it is not logged.
     */
    @ExceptionHandler(PlatformAdminInvitationLoginRequiredException.class)
    public ResponseEntity<RestError> handleException(PlatformAdminInvitationLoginRequiredException e) {
        return errorBuilder.buildWithoutLogging(message("error.platform.admin.invitation.login.required"),
                RestErrorCode.PLATFORM_ADMIN_INVITATION_LOGIN_REQUIRED, null, HttpStatus.CONFLICT);
    }

    /**
     * Logged by the acceptance itself, with the count of failed attempts; not again here.
     */
    @ExceptionHandler(ConfirmationCodeInvalidException.class)
    public ResponseEntity<RestError> handleException(ConfirmationCodeInvalidException e) {
        return errorBuilder.buildWithoutLogging(message("error.platform.admin.invitation.code.invalid"),
                RestErrorCode.PLATFORM_ADMIN_INVITATION_CODE_INVALID, null, HttpStatus.BAD_REQUEST);
    }

    /**
     * Logged by the acceptance itself, when it revokes the link; not again here.
     */
    @ExceptionHandler(ConfirmationCodeAttemptsExhaustedException.class)
    public ResponseEntity<RestError> handleException(ConfirmationCodeAttemptsExhaustedException e) {
        return errorBuilder.buildWithoutLogging(message("error.platform.admin.invitation.code.attempts.exhausted"),
                RestErrorCode.PLATFORM_ADMIN_INVITATION_CODE_ATTEMPTS_EXHAUSTED, null, HttpStatus.BAD_REQUEST);
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, List.of(args).toArray(), LocaleContextHolder.getLocale());
    }
}
