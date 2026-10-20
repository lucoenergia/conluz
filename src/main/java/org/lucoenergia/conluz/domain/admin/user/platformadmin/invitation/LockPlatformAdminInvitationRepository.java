package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Locks platform-admin invitation rows until the end of the caller's transaction. Lock order: the account's user row
 * first, then the invitation row, then the token rows.
 */
public interface LockPlatformAdminInvitationRepository {

    /**
     * @return whether the account has a pending invitation, now locked
     */
    boolean lockPending(UserId userId);

    /**
     * @return whether the invitation exists, now locked
     */
    boolean lock(UUID invitationId);
}
