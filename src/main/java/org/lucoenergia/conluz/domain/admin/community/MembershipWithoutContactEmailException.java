package org.lucoenergia.conluz.domain.admin.community;

import java.util.UUID;

/**
 * The member record has no contact email, so there is nowhere to send an invitation. The account's email is never
 * used instead: it may have been entered by another community (ADR-0003 §1).
 */
public class MembershipWithoutContactEmailException extends RuntimeException {

    private final UUID communityId;
    private final UUID userId;

    public MembershipWithoutContactEmailException(UUID communityId, UUID userId) {
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
