package org.lucoenergia.conluz.domain.admin.supply;

/**
 * The owner of a supply being created is not a user, or is a user without a membership in the supply's community.
 * The two are one exception so that the answer does not tell them apart (SUP-002); the personal ID is not carried,
 * so that it is never logged or echoed.
 */
public class SupplyOwnerNotFoundException extends RuntimeException {
}
