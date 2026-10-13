package org.lucoenergia.conluz.domain.admin.user.password.reset;

/**
 * What became of a password recovery request. Only ever logged: the caller is answered the same way in every case.
 */
public enum PasswordResetRequestOutcome {
    /**
     * A token was issued and its email handed over for sending once the transaction commits.
     */
    SENT,
    /**
     * The user has no email address.
     */
    NO_EMAIL,
    /**
     * The user has no password. Their first password is set only by accepting an invitation, never by recovery
     * (ADR-0003 §7).
     */
    NO_PASSWORD,
    /**
     * No user has that personal ID.
     */
    UNKNOWN,
    /**
     * The user is disabled.
     */
    DISABLED,
    /**
     * The user has already been sent the daily maximum of links.
     */
    OVER_LIMIT,
    /**
     * The email could not be built, because the public web URL the link points to is not configured. No token was
     * issued.
     */
    FAILED
}
