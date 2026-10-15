package org.lucoenergia.conluz.domain.admin.community;

import java.util.UUID;

/**
 * The pending membership has already been sent its invitation. Sending it again is a resend, which is limited per
 * membership and day (ADR-0003 §5) and is a separate operation ({@code ResendInvitationService}).
 */
public class MembershipAlreadyInvitedException extends RuntimeException {

    private final UUID communityId;
    private final UUID userId;

    public MembershipAlreadyInvitedException(UUID communityId, UUID userId) {
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
