package org.lucoenergia.conluz.domain.admin.community.membership.register;

/**
 * A rule of the registration that a value of an imported row breaks. Each rule of the registration body has one, and
 * so does the registration's own check of the personal ID.
 */
public enum MemberImportConstraint {

    /**
     * The column is required and the value is empty or blank.
     */
    REQUIRED,
    /**
     * The value holds only characters that normalisation removes: whitespace, dots and hyphens.
     */
    NOT_EMPTY_ONCE_NORMALISED,
    /**
     * The personal ID, once normalised, is not a valid DNI, NIE or NIF (#398).
     */
    INVALID_PERSONAL_ID,
    /**
     * The number is below the minimum, given as the parameter {@code min}.
     */
    MIN,
    /**
     * The value is not an email address.
     */
    EMAIL,
    /**
     * The value is not a whole number.
     */
    NOT_AN_INTEGER
}
