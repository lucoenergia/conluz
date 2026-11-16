package org.lucoenergia.conluz.domain.admin.supply.transfer;

/**
 * A supply transfer's effective date is on or before the effective date of the supply's previous transfer (SUP-010).
 * Nothing is written.
 */
public class SupplyTransferDateNotAfterPreviousException extends RuntimeException {
}
