package org.lucoenergia.conluz.domain.production.plant.lock;

import org.lucoenergia.conluz.domain.shared.PlantId;

/**
 * Locks a plant row for deletion, for the rest of the caller's transaction. It must be called inside a transaction:
 * outside one, the lock would be released at once.
 *
 * <p>The lock is exclusive: it waits for any transaction that is inserting a sharing agreement, a partition
 * coefficient or a Huawei configuration that references the plant, so a check made after it sees what that
 * transaction wrote.</p>
 */
public interface LockPlantRepository {

    /**
     * @return {@code true} if the plant exists and is now locked, {@code false} if there is no such plant
     */
    boolean lock(PlantId plantId);
}
