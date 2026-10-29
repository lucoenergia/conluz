package org.lucoenergia.conluz.domain.admin.supply.create;

/**
 * A rule that a value of an imported row of supplies breaks.
 */
public enum SupplyImportConstraint {

    /**
     * The column is required and the value is empty or blank.
     */
    REQUIRED,
    /**
     * The code, once upper-cased, holds whitespace or is neither 20 nor 22 characters long (SUP-004).
     */
    INVALID_CUPS
}
