package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.UUID;

public interface CreatePlatformAdminInvitationRepository {

    /**
     * Stores a pending invitation, with no failed code attempts.
     */
    void create(UUID invitationId, UserId userId, String email, String fullName, UserId invitedBy, Instant invitedAt,
                String confirmationCodeHash);
}
