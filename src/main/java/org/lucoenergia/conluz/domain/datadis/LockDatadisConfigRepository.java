package org.lucoenergia.conluz.domain.datadis;

import java.util.UUID;

/**
 * Locks a community's Datadis configuration row for the rest of the caller's transaction, so that writes to the same
 * configuration run one after another. It must be called inside a transaction: outside one, the lock would be
 * released at once.
 */
public interface LockDatadisConfigRepository {

    /**
     * @return {@code true} if the community has a configuration and it is now locked, {@code false} if it has none
     */
    boolean lock(UUID communityId);
}
