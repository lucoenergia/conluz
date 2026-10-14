package org.lucoenergia.conluz.domain.admin.community;

import java.util.UUID;

/**
 * The pending membership has never been sent its invitation, so there is nothing to resend: its first invitation is
 * sent with {@code SendInvitationService}, which records it.
 */
public class MembershipNotInvitedException extends RuntimeException {

    private final UUID communityId;
    private final UUID userId;

    public MembershipNotInvitedException(UUID communityId, UUID userId) {
        this.communityId = communityId;
        this.userId = userId;
    }

    public UUID getCommunityId() {
        return communityId;
    }

    public UUID getUserId() {
        return userId;
    }
}
