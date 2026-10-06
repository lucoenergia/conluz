package org.lucoenergia.conluz.domain.admin.user.password;

/**
 * The new password supplied with a password change is exactly equal to the current one.
 */
public class PasswordUnchangedException extends RuntimeException {

    public PasswordUnchangedException() {
        super("The new password is equal to the current one");
    }
}
