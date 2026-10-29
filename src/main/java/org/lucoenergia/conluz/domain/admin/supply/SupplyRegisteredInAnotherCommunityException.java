package org.lucoenergia.conluz.domain.admin.supply;

/**
 * The CUPS of a supply being written is already registered for a supply of another community, in either of its two
 * lengths and in any case (#532). Neither that community nor the supply's owner is carried, so that neither can be
 * told to the caller.
 */
public class SupplyRegisteredInAnotherCommunityException extends RuntimeException {
}
