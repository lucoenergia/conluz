package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * The invitation has been sent as many times as allowed in the last 24 hours.
 */
public class PlatformAdminInvitationResendLimitException extends RuntimeException {

    private final UUID userId;
    private final long retryAfterSeconds;

    public PlatformAdminInvitationResendLimitException(UserId userId, long retryAfterSeconds) {
        super("The platform-admin invitation of user with ID '" + userId.getId()
                + "' cannot be sent again for " + retryAfterSeconds + " seconds");
        this.userId = userId.getId();
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
