package org.lucoenergia.conluz.domain.admin.community;

import java.util.UUID;

/**
 * The membership is the community's last active admin, so it is neither demoted to member nor deleted (MEM-033): a
 * community keeps at least one admin who can act for it. Promoting another active member to admin first makes the
 * change possible.
 */
public class MembershipLastActiveAdminException extends RuntimeException {

    private final UUID communityId;
    private final UUID userId;

    public MembershipLastActiveAdminException(UUID communityId, UUID userId) {
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
