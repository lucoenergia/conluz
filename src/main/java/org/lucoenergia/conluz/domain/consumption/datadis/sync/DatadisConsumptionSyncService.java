package org.lucoenergia.conluz.domain.consumption.datadis.sync;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.time.LocalDate;

public interface DatadisConsumptionSyncService {

    /**
     * Synchronizes the consumptions for all supplies within the specified date range.
     * It retrieves all supplies from the repository and for each supply, it retrieves
     * the monthly consumptions between the given start and end dates.
     *
     * @param startDate the start date from which to synchronize consumptions, inclusive
     * @param endDate the end date until which to synchronize consumptions, inclusive
     */
    void synchronizeConsumptions(LocalDate startDate, LocalDate endDate);

    /**
     * Synchronizes the consumptions for a single supply within the specified date range.
     * It retrieves the supply by its code and synchronizes the monthly consumptions
     * between the given start and end dates.
     *
     * @param startDate the start date from which to synchronize consumptions, inclusive
     * @param endDate the end date until which to synchronize consumptions, inclusive
     * @param supplyCode the code of the supply to synchronize
     * @throws org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException if the supply code is not found
     */
    void synchronizeConsumptions(LocalDate startDate, LocalDate endDate, SupplyCode supplyCode);
}
