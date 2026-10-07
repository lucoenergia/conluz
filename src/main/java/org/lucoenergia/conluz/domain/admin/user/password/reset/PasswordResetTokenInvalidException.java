package org.lucoenergia.conluz.domain.admin.user.password.reset;

/**
 * A password reset token that cannot be used, for whatever reason. The reason is deliberately not told, and the
 * token is never part of the message.
 */
public class PasswordResetTokenInvalidException extends RuntimeException {

    public PasswordResetTokenInvalidException() {
        super("The password reset token is not valid");
    }
}
