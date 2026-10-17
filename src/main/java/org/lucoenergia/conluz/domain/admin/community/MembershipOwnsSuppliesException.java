package org.lucoenergia.conluz.domain.admin.community;

import java.util.UUID;

/**
 * The user owns supplies in the community, so their membership there is not deleted (MEM-029): every supply's owner
 * belongs to the supply's community (#341). Disabling the membership withdraws the person's access and keeps the
 * supplies.
 */
public class MembershipOwnsSuppliesException extends RuntimeException {

    private final UUID communityId;
    private final UUID userId;

    public MembershipOwnsSuppliesException(UUID communityId, UUID userId) {
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
