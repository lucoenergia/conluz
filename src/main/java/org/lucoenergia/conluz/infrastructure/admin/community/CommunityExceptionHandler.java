package org.lucoenergia.conluz.infrastructure.admin.community;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
}
