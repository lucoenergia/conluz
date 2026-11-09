package org.lucoenergia.conluz.domain.admin.supply.sync;

import java.util.UUID;

public interface DatadisSuppliesSyncService {

    /**
     * Synchronizes the supplies for all users retrieving data from datadis
     */
    void synchronizeSupplies();

    /**
     * Synchronizes only the supplies belonging to the given community, retrieving data from
     * datadis with the community's own configuration. No supply outside the community is read or written.
     * A community with no Datadis configuration, or a disabled one, is refused as the consumption sync refuses it,
     * and Datadis is not called.
     */
    void synchronizeSupplies(UUID communityId);
}
