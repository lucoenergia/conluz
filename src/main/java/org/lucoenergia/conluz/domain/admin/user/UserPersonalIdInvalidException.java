package org.lucoenergia.conluz.domain.admin.user;

/**
 * A personal ID that is not a valid DNI, NIE or NIF once normalised (#398). It deliberately carries no personal ID
 * and does not say which rule the value breaks, so reporting it never echoes the value back and every refusal is the
 * same, whether or not an account holds that value.
 */
public class UserPersonalIdInvalidException extends RuntimeException {

    public UserPersonalIdInvalidException() {
        super("The personal ID is not a valid DNI, NIE or NIF");
    }
}
