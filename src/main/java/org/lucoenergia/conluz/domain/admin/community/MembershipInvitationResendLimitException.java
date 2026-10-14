package org.lucoenergia.conluz.domain.admin.community;

import java.util.UUID;

/**
 * The membership's invitation has been sent as many times as allowed in the last 24 hours (ADR-0003 §5).
 */
public class MembershipInvitationResendLimitException extends RuntimeException {

    private final UUID communityId;
    private final UUID userId;
    private final long retryAfterSeconds;

    public MembershipInvitationResendLimitException(UUID communityId, UUID userId, long retryAfterSeconds) {
        this.communityId = communityId;
        this.userId = userId;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public UUID getCommunityId() {
        return communityId;
    }

    public UUID getUserId() {
        return userId;
    }

    /**
     * @return the whole number of seconds until a resend will be accepted again
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
