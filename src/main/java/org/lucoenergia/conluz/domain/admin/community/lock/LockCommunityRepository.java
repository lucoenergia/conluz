package org.lucoenergia.conluz.domain.admin.community.lock;

import java.util.UUID;

/**
 * Locks a community row for the rest of the caller's transaction, so that flows that must see every change to the
 * community's admins run one after another (MEM-033), and so do replacements of its contact channels. It must be
 * called inside a transaction: outside one, the lock would be released at once.
 *
 * <p><b>Lock ordering.</b> Community row, then user row, then membership row, then token rows. Call this before
 * {@link org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository}.</p>
 */
public interface LockCommunityRepository {

    /**
     * @return {@code true} if the community exists and is now locked, {@code false} if there is no such community
     */
    boolean lock(UUID communityId);
}
