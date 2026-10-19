package org.lucoenergia.conluz.domain.admin.community.membership.register;

public enum MemberImportRowOutcome {

    /**
     * The member is registered, with a pending membership; whether the person already had an account is not told.
     */
    REGISTERED,
    /**
     * The person already has a membership in the community, in any state, possibly from an earlier row of the same
     * file. Nothing is changed.
     */
    ALREADY_MEMBER,
    /**
     * A value breaks a rule of the registration. Nothing is registered.
     */
    INVALID,
    /**
     * The personal ID, once normalised, is not a valid DNI, NIE or NIF (#398). Nothing is registered.
     */
    INVALID_PERSONAL_ID,
    /**
     * The row does not have as many cells as the header. Nothing is registered.
     */
    MALFORMED
}
