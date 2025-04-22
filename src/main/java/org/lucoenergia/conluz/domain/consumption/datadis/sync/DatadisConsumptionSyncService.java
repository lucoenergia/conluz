package org.lucoenergia.conluz.domain.consumption.datadis.sync;

public interface DatadisConsumptionSyncService {

    /**
     * Synchronizes the consumptions for all supplies.
     * It retrieves all supplies from the repository, and for each supply it retrieves the monthly consumptions
     * based on the validity date of the supply. The method iterates through the validity dates until it reaches
     * the current date, retrieving the monthly consumptions for each month and year.
     */
    void synchronizeConsumptions();
}
