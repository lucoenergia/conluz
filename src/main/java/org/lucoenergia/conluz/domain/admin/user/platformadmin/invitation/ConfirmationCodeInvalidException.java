package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

/**
 * The confirmation code of a platform-admin invitation is missing or wrong. Nothing was granted; the attempt was
 * counted against the invitation and the client address. The code is never part of the message.
 */
public class ConfirmationCodeInvalidException extends RuntimeException {

    public ConfirmationCodeInvalidException() {
        super("The confirmation code is missing or wrong");
    }
}
