package org.lucoenergia.conluz.infrastructure.admin.community;

import org.lucoenergia.conluz.domain.admin.community.CommunityAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyInvitedException;
import org.lucoenergia.conluz.domain.admin.community.MembershipInvitationResendLimitException;
import org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipDisabledException;
import org.lucoenergia.conluz.domain.admin.community.MembershipOwnsSuppliesException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotInvitedException;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationLoginRequiredException;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationTokenInvalidException;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class CommunityExceptionHandler {

    private final MessageSource messageSource;
    private final ErrorBuilder errorBuilder;

    public CommunityExceptionHandler(MessageSource messageSource, ErrorBuilder errorBuilder) {
        this.messageSource = messageSource;
        this.errorBuilder = errorBuilder;
    }

    @ExceptionHandler(CommunityNotFoundException.class)
    public ResponseEntity<RestError> handleException(CommunityNotFoundException e) {
        String message = messageSource.getMessage(
                "error.community.not.found",
                Collections.singletonList(e.getId() != null ? e.getId().toString() : "").toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.NOT_FOUND);
    }

    /**
     * A 404 rather than a 403: the caller reaching this point has already been authorized for the
     * community, so nothing is leaked by saying the membership is not there.
     */
    @ExceptionHandler(MembershipNotFoundException.class)
    public ResponseEntity<RestError> handleException(MembershipNotFoundException e) {
        String message = messageSource.getMessage(
                "error.membership.not.found",
                List.of(
                        e.getUserId() != null ? e.getUserId().toString() : "",
                        e.getCommunityId() != null ? e.getCommunityId().toString() : ""
                ).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, HttpStatus.NOT_FOUND);
    }

    /**
     * A 409 for the same reason a duplicate membership is one: the body is well-formed and the
     * caller is authorized, and what stops the request is that {@code code} or {@code legalId} is
     * already taken by another community. Both columns are unique in the database.
     *
     * <p>This exception was thrown from the moment communities gained uniqueness checks but was
     * never mapped, so a duplicate left the application as a 500 with no {@code RestError} body.
     * {@code field} names which of the two collided, carried as a param so a client can mark the
     * offending input rather than guessing from the message.
     */
    @ExceptionHandler(CommunityAlreadyExistsException.class)
    public ResponseEntity<RestError> handleException(CommunityAlreadyExistsException e) {
        String field = e.getField() != null ? e.getField() : "";
        String value = e.getValue() != null ? e.getValue() : "";

        String message = messageSource.getMessage(
                "error.community.already.exists",
                List.of(field, value).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.COMMUNITY_ALREADY_EXISTS,
                Map.of("field", field, "value", value), HttpStatus.CONFLICT);
    }

    /**
     * A 409 rather than a 400: the body is well-formed and the caller is authorized, and what
     * stops the request is the state of the resource -- the membership is already there. This is
     * the mapping {@code docs/security/authorization-policy.md} prescribes for a state conflict,
     * which it says must never be a 400.
     *
     * <p>Note this differs from the older duplicate handling for users, supplies and plants, which
     * answers 400. Those are not retrofitted here: changing them would alter contracts this change
     * has no reason to touch.
     */
    @ExceptionHandler(MembershipAlreadyExistsException.class)
    public ResponseEntity<RestError> handleException(MembershipAlreadyExistsException e) {
        String userId = e.getUserId() != null ? e.getUserId().toString() : "";
        String communityId = e.getCommunityId() != null ? e.getCommunityId().toString() : "";

        String message = messageSource.getMessage(
                "error.membership.already.exists",
                List.of(userId, communityId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.MEMBERSHIP_ALREADY_EXISTS,
                Map.of("userId", userId, "communityId", communityId), HttpStatus.CONFLICT);
    }

    /**
     * A 409: the membership has already been sent its invitation, and sending another one is a resend (#393).
     */
    @ExceptionHandler(MembershipAlreadyInvitedException.class)
    public ResponseEntity<RestError> handleException(MembershipAlreadyInvitedException e) {
        String userId = e.getUserId() != null ? e.getUserId().toString() : "";
        String communityId = e.getCommunityId() != null ? e.getCommunityId().toString() : "";

        String message = messageSource.getMessage(
                "error.membership.already.invited",
                List.of(userId, communityId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.MEMBERSHIP_ALREADY_INVITED,
                Map.of("userId", userId, "communityId", communityId), HttpStatus.CONFLICT);
    }

    /**
     * A 400: the member record has no contact email to send the invitation to. Adding one to the record is what
     * the caller has to change.
     */
    @ExceptionHandler(MembershipWithoutContactEmailException.class)
    public ResponseEntity<RestError> handleException(MembershipWithoutContactEmailException e) {
        String userId = e.getUserId() != null ? e.getUserId().toString() : "";
        String communityId = e.getCommunityId() != null ? e.getCommunityId().toString() : "";

        String message = messageSource.getMessage(
                "error.membership.without.contact.email",
                List.of(userId, communityId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.MEMBERSHIP_WITHOUT_CONTACT_EMAIL,
                Map.of("userId", userId, "communityId", communityId), HttpStatus.BAD_REQUEST);
    }

    /**
     * A 503: the request is valid, but the server cannot send the invitation right now. Nothing was issued, so the
     * same request succeeds once it can. Not logged here: the service has already logged why, naming only the email
     * category.
     */
    @ExceptionHandler(MembershipInvitationUnavailableException.class)
    public ResponseEntity<RestError> handleException(MembershipInvitationUnavailableException e) {
        String message = messageSource.getMessage(
                "error.membership.invitation.unavailable",
                null,
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.buildWithoutLogging(message, RestErrorCode.MEMBERSHIP_INVITATION_UNAVAILABLE, Map.of(),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * A 409: the membership has never been invited, so there is nothing to resend. Its first invitation is sent with
     * {@code sendInvitation}.
     */
    @ExceptionHandler(MembershipNotInvitedException.class)
    public ResponseEntity<RestError> handleException(MembershipNotInvitedException e) {
        String userId = e.getUserId() != null ? e.getUserId().toString() : "";
        String communityId = e.getCommunityId() != null ? e.getCommunityId().toString() : "";

        String message = messageSource.getMessage(
                "error.membership.not.invited",
                List.of(userId, communityId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.MEMBERSHIP_NOT_INVITED,
                Map.of("userId", userId, "communityId", communityId), HttpStatus.CONFLICT);
    }

    /**
     * A 409: the membership is disabled, so it is not sent an invitation. Revoking its invitation still works.
     */
    @ExceptionHandler(MembershipDisabledException.class)
    public ResponseEntity<RestError> handleException(MembershipDisabledException e) {
        String userId = e.getUserId() != null ? e.getUserId().toString() : "";
        String communityId = e.getCommunityId() != null ? e.getCommunityId().toString() : "";

        String message = messageSource.getMessage(
                "error.membership.disabled",
                List.of(userId, communityId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.MEMBERSHIP_DISABLED,
                Map.of("userId", userId, "communityId", communityId), HttpStatus.CONFLICT);
    }

    /**
     * A 409: the user owns supplies in the community, so their membership there is not deleted (MEM-029).
     */
    @ExceptionHandler(MembershipOwnsSuppliesException.class)
    public ResponseEntity<RestError> handleException(MembershipOwnsSuppliesException e) {
        String userId = e.getUserId().toString();
        String communityId = e.getCommunityId().toString();

        String message = messageSource.getMessage(
                "error.membership.owns.supplies",
                List.of(userId, communityId).toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.build(message, RestErrorCode.MEMBERSHIP_OWNS_SUPPLIES,
                Map.of("userId", userId, "communityId", communityId), HttpStatus.CONFLICT);
    }

    /**
     * A 429: the invitation has been sent as many times as allowed in the last 24 hours. The wait is in the
     * {@code Retry-After} header, as for throttled authentication (#332), and in the error's parameters. Not logged
     * here: the service has already logged the refusal.
     */
    @ExceptionHandler(MembershipInvitationResendLimitException.class)
    public ResponseEntity<RestError> handleException(MembershipInvitationResendLimitException e) {
        String userId = e.getUserId() != null ? e.getUserId().toString() : "";
        String communityId = e.getCommunityId() != null ? e.getCommunityId().toString() : "";
        String retryAfterSeconds = String.valueOf(e.getRetryAfterSeconds());

        String message = messageSource.getMessage(
                "error.membership.invitation.resend.limit",
                List.of(userId, communityId, retryAfterSeconds).toArray(),
                LocaleContextHolder.getLocale()
        );
        ResponseEntity<RestError> response = errorBuilder.buildWithoutLogging(message,
                RestErrorCode.MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED,
                Map.of("userId", userId, "communityId", communityId, "retryAfterSeconds", retryAfterSeconds),
                HttpStatus.TOO_MANY_REQUESTS);
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds)
                .body(response.getBody());
    }

    /**
     * The same answer whatever made the invitation token unusable. Not logged here: the throttling logs each
     * unusable token once, with the client address.
     */
    @ExceptionHandler(InvitationTokenInvalidException.class)
    public ResponseEntity<RestError> handleException(InvitationTokenInvalidException e) {
        String message = messageSource.getMessage(
                "error.membership.invitation.token.invalid",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.buildWithoutLogging(message, RestErrorCode.MEMBERSHIP_INVITATION_TOKEN_INVALID, null,
                HttpStatus.BAD_REQUEST);
    }

    /**
     * A 409: the invitation is valid, but its account has a password, so it is accepted only after logging in. An
     * expected answer that the client turns into a login, so it is not logged.
     */
    @ExceptionHandler(InvitationLoginRequiredException.class)
    public ResponseEntity<RestError> handleException(InvitationLoginRequiredException e) {
        String message = messageSource.getMessage(
                "error.membership.invitation.login.required",
                List.of().toArray(),
                LocaleContextHolder.getLocale()
        );
        return errorBuilder.buildWithoutLogging(message, RestErrorCode.MEMBERSHIP_INVITATION_LOGIN_REQUIRED, null,
                HttpStatus.CONFLICT);
    }
}
