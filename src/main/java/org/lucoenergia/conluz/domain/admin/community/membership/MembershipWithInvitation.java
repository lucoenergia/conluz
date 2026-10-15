package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;

import java.time.Instant;

/**
 * A membership, with when its invitation link expires.
 *
 * @param membership          the membership
 * @param invitationExpiresAt the expiry of the membership's valid {@code INVITATION} token, or null when it has none:
 *                            never sent, used, revoked or expired
 */
public record MembershipWithInvitation(CommunityMembership membership, Instant invitationExpiresAt) {
}
