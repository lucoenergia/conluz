package org.lucoenergia.conluz.domain.admin.community.admins;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;

import java.time.Instant;

/**
 * A membership with the community admin role, as the platform admin sees it (ADR-0003 §3): the community's member
 * record, the personal id the person is identified by, and the state of the membership and of its invitation.
 *
 * @param personalId          the person's DNI/NIE/NIF, normalised
 * @param invitationExpiresAt when the membership's invitation link expires, null when it has none
 */
public record CommunityAdmin(String personalId, CommunityMembership membership, Instant invitationExpiresAt) {
}
