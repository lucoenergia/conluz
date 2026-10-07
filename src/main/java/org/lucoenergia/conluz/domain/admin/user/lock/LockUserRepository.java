package org.lucoenergia.conluz.domain.admin.user.lock;

import org.lucoenergia.conluz.domain.shared.UserId;

/**
 * Locks a user row for the rest of the caller's transaction, so that flows writing the same user run one after
 * another. It must be called inside a transaction: outside one, the lock would be released at once.
 *
 * <p>Lock the user row before that user's one-time token rows; see
 * {@link org.lucoenergia.conluz.domain.admin.user.token.ConsumeOneTimeTokenService} for the order.</p>
 */
public interface LockUserRepository {

    /**
     * @return {@code true} if the user exists and is now locked, {@code false} if there is no such user
     */
    boolean lock(UserId userId);
}
