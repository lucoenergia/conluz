package org.lucoenergia.conluz.domain.admin.community.membership.lock;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Locks a membership row for the rest of the caller's transaction, so that flows writing the same membership run one
 * after another. It must be called inside a transaction: outside one, the lock would be released at once.
 *
 * <p><b>Lock ordering.</b> User row, then membership row, then token rows. Lock the membership's user with
 * {@link org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository} before calling this, and call this before
 * touching that membership's one-time tokens; see
 * {@link org.lucoenergia.conluz.domain.admin.user.token.ConsumeOneTimeTokenService} for the order.</p>
 */
public interface LockMembershipRepository {

    /**
     * @return {@code true} if the user's membership in the community exists and is now locked, {@code false} if
     * there is no such membership
     */
    boolean lock(UserId userId, UUID communityId);
}
