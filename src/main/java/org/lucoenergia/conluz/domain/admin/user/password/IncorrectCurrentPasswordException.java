package org.lucoenergia.conluz.domain.admin.user.password;

/**
 * The current password supplied with a password change does not match the stored one.
 */
public class IncorrectCurrentPasswordException extends RuntimeException {

    public IncorrectCurrentPasswordException() {
        super("The current password is incorrect");
    }
}
