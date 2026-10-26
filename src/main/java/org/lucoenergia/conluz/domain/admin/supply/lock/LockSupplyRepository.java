package org.lucoenergia.conluz.domain.admin.supply.lock;

import org.lucoenergia.conluz.domain.shared.SupplyId;

/**
 * Locks a supply row for the rest of the caller's transaction, either to delete it or to write a row that references
 * it. It must be called inside a transaction: outside one, the lock would be released at once.
 *
 * <p>The two locks conflict with each other, so a deletion and a writer of a referencing row run one after another
 * (SUP-001): a writer that comes second finds the supply gone, and a deletion that comes second sees what the writer
 * wrote. Lock ordering: plant row, then supply row, then their child rows, all after any community, user, membership
 * or token row.</p>
 */
public interface LockSupplyRepository {

    /**
     * Locks the supply exclusively, before deleting it: it waits for any transaction that holds
     * {@link #lockAsReferenced} or is inserting a row that references the supply.
     *
     * @return {@code true} if the supply exists and is now locked, {@code false} if there is no such supply
     */
    boolean lock(SupplyId supplyId);

    /**
     * Locks the supply against deletion, before writing a plant or a partition coefficient that references it. It
     * waits for a deletion in progress, and does not conflict with other writers or with updates of the supply.
     *
     * @return {@code true} if the supply exists and is now locked, {@code false} if there is no such supply
     */
    boolean lockAsReferenced(SupplyId supplyId);
}
