package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

/**
 * The outcome of a bulk send of first invitations.
 *
 * @param sent      how many memberships were invited
 * @param remaining how many memberships of the community are still eligible for a first invitation
 */
public record SentInvitations(int sent, long remaining) {
}
