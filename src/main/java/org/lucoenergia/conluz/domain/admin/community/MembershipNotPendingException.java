package org.lucoenergia.conluz.domain.admin.community;

import java.util.UUID;

/**
 * An invitation was asked for a membership that is no longer pending: it has been accepted already. Only a pending
 * membership can be invited (ADR-0003 §2).
 */
public class MembershipNotPendingException extends RuntimeException {

    private final UUID communityId;
    private final UUID userId;

    public MembershipNotPendingException(UUID communityId, UUID userId) {
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
