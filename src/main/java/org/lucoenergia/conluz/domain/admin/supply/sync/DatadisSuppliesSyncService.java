package org.lucoenergia.conluz.domain.admin.supply.sync;

import java.util.UUID;

public interface DatadisSuppliesSyncService {

    /**
     * Synchronizes the supplies for all users retrieving data from datadis
     */
    void synchronizeSupplies();

    /**
     * Synchronizes only the supplies belonging to the given community, retrieving data from
     * datadis. No supply outside the community is read or written.
     */
    void synchronizeSupplies(UUID communityId);
}
