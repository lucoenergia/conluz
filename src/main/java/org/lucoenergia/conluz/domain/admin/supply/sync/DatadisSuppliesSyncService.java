package org.lucoenergia.conluz.domain.admin.supply.sync;

public interface DatadisSuppliesSyncService {

    /**
     * Synchronizes the supplies for all users retrieving data from datadis.es
     */
    void synchronizeSupplies();
}
