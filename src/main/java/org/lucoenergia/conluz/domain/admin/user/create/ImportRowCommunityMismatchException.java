package org.lucoenergia.conluz.domain.admin.user.create;

/**
 * Thrown when a row of a bulk user import names a community other than the community of the import.
 * It deliberately carries no community identifier, so reporting it never reveals whether the other
 * community exists.
 */
public class ImportRowCommunityMismatchException extends RuntimeException {
}
