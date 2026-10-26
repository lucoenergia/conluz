package org.lucoenergia.conluz.domain.production.plant.lock;

import org.lucoenergia.conluz.domain.shared.PlantId;

/**
 * Locks a plant row for the rest of the caller's transaction, either to delete it or to write a row that references
 * it. It must be called inside a transaction: outside one, the lock would be released at once.
 *
 * <p>The two locks conflict with each other, so a deletion and a writer of a referencing row run one after another
 * (PLT-001): a writer that comes second finds the plant gone, and a deletion that comes second sees what the writer
 * wrote. Lock ordering: plant row, then supply row, then their child rows (such as the Huawei configuration), all
 * after any community, user, membership or token row.</p>
 */
public interface LockPlantRepository {

    /**
     * Locks the plant exclusively, before deleting it: it waits for any transaction that holds
     * {@link #lockAsReferenced} or is inserting a row that references the plant.
     *
     * @return {@code true} if the plant exists and is now locked, {@code false} if there is no such plant
     */
    boolean lock(PlantId plantId);

    /**
     * Locks the plant against deletion, before writing a sharing agreement, a partition coefficient or a Huawei
     * configuration that references it. It waits for a deletion in progress, and does not conflict with other writers
     * or with updates of the plant.
     *
     * @return {@code true} if the plant exists and is now locked, {@code false} if there is no such plant
     */
    boolean lockAsReferenced(PlantId plantId);
}
