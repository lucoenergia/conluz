package org.lucoenergia.conluz.domain.consumption.datadis.sync;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.time.LocalDate;
import java.util.UUID;

public interface DatadisConsumptionSyncService {

    void synchronizeConsumptions(LocalDate startDate, LocalDate endDate);

    void synchronizeConsumptions(LocalDate startDate, LocalDate endDate, SupplyCode supplyCode);

    void synchronizeConsumptions(UUID communityId, LocalDate startDate, LocalDate endDate);

    /**
     * Synchronizes a single supply identified by its code, requiring it to belong to the given
     * community so a sync job for one community cannot reach another community's supply.
     */
    void synchronizeConsumptions(UUID communityId, LocalDate startDate, LocalDate endDate, SupplyCode supplyCode);
}
