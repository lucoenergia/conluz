package org.lucoenergia.conluz.domain.admin.user;

/**
 * A user with the same (normalised) personal ID already exists. It deliberately carries no personal
 * ID, so reporting it never echoes the value back and the error cannot be used to probe which personal
 * IDs are registered.
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException() {
        super();
    }

    public UserAlreadyExistsException(Throwable cause) {
        super(cause);
    }
}
