package org.lucoenergia.conluz.domain.admin.community.membership.register;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;

/**
 * A member just registered in a community: the personal id as the community admin typed it, normalised, and the
 * pending membership with its member record. The membership's account is not described here: nothing about it may
 * reach the community admin (ADR-0003 §1).
 */
public record RegisteredMember(String personalId, CommunityMembership membership) {
}
