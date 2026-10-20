package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes to an invitation the caller has locked with {@link LockPlatformAdminInvitationRepository}.
 */
public interface UpdatePlatformAdminInvitationRepository {

    /**
     * Replaces the confirmation code's hash, and resets the count of failed code attempts.
     */
    void replaceConfirmationCode(UUID invitationId, String confirmationCodeHash);

    /**
     * Counts one more missing or wrong confirmation code.
     *
     * @return the count of failed code attempts, this one included
     */
    int recordFailedCodeAttempt(UUID invitationId);

    void markAccepted(UUID invitationId, Instant acceptedAt);
}
