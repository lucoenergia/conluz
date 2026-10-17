package org.lucoenergia.conluz.domain.admin.supply.lock;

import org.lucoenergia.conluz.domain.shared.SupplyId;

/**
 * Locks a supply row for deletion, for the rest of the caller's transaction. It must be called inside a transaction:
 * outside one, the lock would be released at once.
 *
 * <p>The lock is exclusive: it waits for any transaction that is inserting a plant or a partition coefficient that
 * references the supply, so a check made after it sees what that transaction wrote.</p>
 */
public interface LockSupplyRepository {

    /**
     * @return {@code true} if the supply exists and is now locked, {@code false} if there is no such supply
     */
    boolean lock(SupplyId supplyId);
}
