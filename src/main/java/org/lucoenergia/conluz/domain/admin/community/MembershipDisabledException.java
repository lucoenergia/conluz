package org.lucoenergia.conluz.domain.admin.community;

import java.util.UUID;

/**
 * The pending membership is disabled, so it is not sent an invitation: a disabled membership grants nothing, and
 * inviting someone to it would ask them to join a community they cannot use.
 */
public class MembershipDisabledException extends RuntimeException {

    private final UUID communityId;
    private final UUID userId;

    public MembershipDisabledException(UUID communityId, UUID userId) {
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
