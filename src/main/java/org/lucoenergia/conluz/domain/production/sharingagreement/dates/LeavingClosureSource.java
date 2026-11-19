package org.lucoenergia.conluz.domain.production.sharingagreement.dates;

/**
 * Where the date of a leaving closure came from.
 */
public enum LeavingClosureSource {
    /** The request listed the supply with its closing date. */
    REQUEST,
    /** The request is the agreement's first activation and did not list the supply: it is closed at the earliest
     * start of the request, as SHA-009 does. */
    DEFAULT
}
