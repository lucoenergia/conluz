package org.lucoenergia.conluz.domain.admin.supply;

/**
 * A supply code written to a supply is not a CUPS: once upper-cased, it holds whitespace or is neither 20 nor 22
 * characters long (#532). The code is not carried, so that it is never logged or echoed.
 */
public class SupplyCodeInvalidException extends RuntimeException {
}
