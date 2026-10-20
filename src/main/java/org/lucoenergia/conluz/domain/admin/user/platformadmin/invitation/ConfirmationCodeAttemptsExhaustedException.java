package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

/**
 * The fifth missing or wrong confirmation code of a platform-admin invitation: its link has been revoked, and the
 * invitation must be sent again (ADR-0004 §4).
 */
public class ConfirmationCodeAttemptsExhaustedException extends RuntimeException {

    public ConfirmationCodeAttemptsExhaustedException() {
        super("Too many wrong confirmation codes: the invitation must be sent again");
    }
}
