package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * The membership a one-time token was issued for: the membership itself, its user and its community.
 */
public record OneTimeTokenMembership(UUID membershipId, UserId userId, UUID communityId) {
}
