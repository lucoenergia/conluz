package org.lucoenergia.conluz.domain.admin.supply.transfer;

import org.lucoenergia.conluz.domain.admin.supply.SupplyOwnerNotFoundException;

/**
 * The new owner of a supply being transferred is not a user, or is a user without a membership in the supply's
 * community; the two are answered alike, as when a supply is created (SUP-009). Its own type only so that the
 * message points to transferring the supply rather than creating it.
 */
public class SupplyTransferOwnerNotFoundException extends SupplyOwnerNotFoundException {
}
