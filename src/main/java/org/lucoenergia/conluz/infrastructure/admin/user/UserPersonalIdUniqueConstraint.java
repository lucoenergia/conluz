package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.UserAlreadyExistsException;
import org.lucoenergia.conluz.infrastructure.shared.error.PostgresConstraintNameChecker;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The database backstop for personal ID uniqueness. The repositories check for an existing user
 * before writing, but two concurrent requests can both pass that check; the second insert or update
 * is then rejected by this constraint, and must be reported as the same conflict rather than as an
 * unmapped integrity error (a 500).
 */
public final class UserPersonalIdUniqueConstraint {

    public static final String NAME = "users_personal_id_uq";

    private UserPersonalIdUniqueConstraint() {
    }

    /**
     * Translates a violation of this specific constraint, identified by its name, into
     * {@link UserAlreadyExistsException}. Any other integrity violation is returned unchanged, so
     * the caller rethrows it as it was.
     */
    public static RuntimeException translate(DataIntegrityViolationException exception) {
        if (PostgresConstraintNameChecker.matches(exception, NAME)) {
            return new UserAlreadyExistsException(exception);
        }
        return exception;
    }
}
