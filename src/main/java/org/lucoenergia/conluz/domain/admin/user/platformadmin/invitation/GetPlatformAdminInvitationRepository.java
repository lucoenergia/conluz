package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.PendingPlatformAdmin;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetPlatformAdminInvitationRepository {

    /**
     * @return the account's pending invitation, if it has one
     */
    Optional<PlatformAdminInvitation> findPendingByUserId(UserId userId);

    Optional<PlatformAdminInvitation> findById(UUID invitationId);

    /**
     * Every pending invitation whose account does not hold the platform-admin role, with when its valid link
     * expires, in one query.
     *
     * @return the rows by the full name typed by the inviter, then user id
     */
    List<PendingPlatformAdmin> findPendingRows(Instant now);

    /**
     * The row of the account's pending invitation, as {@link #findPendingRows} returns it.
     */
    Optional<PendingPlatformAdmin> findPendingRow(UserId userId, Instant now);
}
