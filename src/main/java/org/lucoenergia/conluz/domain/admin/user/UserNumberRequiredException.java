package org.lucoenergia.conluz.domain.admin.user;

/**
 * A user created with a password must have a number. Only the accounts member registration creates have none (#392).
 */
public class UserNumberRequiredException extends RuntimeException {

    public UserNumberRequiredException() {
        super("A user created with a password must have a number");
    }
}
