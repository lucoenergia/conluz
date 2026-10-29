package org.lucoenergia.conluz.domain.admin.supply;

/**
 * The CUPS of a supply being written is already registered for another supply of the same community, in either of
 * its two lengths and in any case (#532). The code is not carried, so that it is never logged or echoed.
 */
public class SupplyAlreadyExistsException extends RuntimeException {
}
