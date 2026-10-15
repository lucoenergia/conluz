package org.lucoenergia.conluz.domain.admin.community.membership.accept;

import java.util.UUID;

/**
 * What the holder of an invitation link learns before accepting it: the community it is for, and whether the
 * account must log in to accept it, because it already has a password.
 */
public record InvitationInspection(UUID communityId, String communityName, boolean loginRequired) {
}
