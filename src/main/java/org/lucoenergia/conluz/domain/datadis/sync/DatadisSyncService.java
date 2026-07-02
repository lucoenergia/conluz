package org.lucoenergia.conluz.domain.datadis.sync;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Orchestrates the synchronization of Datadis data (consumption and, for plant supplies,
 * derived production) for a community over a date range.
 */
public interface DatadisSyncService {

    void synchronize(UUID communityId, LocalDate startDate, LocalDate endDate);

    /**
     * Synchronizes a single supply identified by its code, requiring it to belong to the given
     * community so a sync job for one community cannot reach another community's supply.
     */
    void synchronize(UUID communityId, LocalDate startDate, LocalDate endDate, SupplyCode supplyCode);
}
