package org.lucoenergia.conluz.domain.admin.supply.create;

/**
 * What happened to one data row of an imported file of supplies.
 */
public enum SupplyImportRowOutcome {

    /**
     * The supply is created.
     */
    CREATED,
    /**
     * A value breaks a rule of the import. Nothing is created.
     */
    INVALID,
    /**
     * The row does not have as many cells as the header. Nothing is created.
     */
    MALFORMED,
    /**
     * The personal ID matches no user, or a user without a membership in the community; the two are not told apart
     * (SUP-002). Nothing is created.
     */
    OWNER_NOT_FOUND,
    /**
     * A supply of the community already has the CUPS, possibly from an earlier row of the same file (SUP-007).
     * Nothing is created.
     */
    ALREADY_EXISTS,
    /**
     * Only a supply of another community has the CUPS (SUP-007). Nothing is created.
     */
    REGISTERED_IN_ANOTHER_COMMUNITY
}
