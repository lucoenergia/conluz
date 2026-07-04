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

    /**
     * Entry point for the manual community sync endpoint. Verifies that Datadis is enabled for the
     * community and then dispatches to the appropriate synchronization depending on whether a
     * specific supply was requested. All the sync orchestration (config gating and dispatch) lives
     * here so the controller does not embed domain logic.
     *
     * @param communityId the community whose supplies are synchronized
     * @param startDate   the first day to synchronize (inclusive)
     * @param endDate     the last day to synchronize (inclusive)
     * @param supplyCode  optional CUPS; when null/blank, all the community's supplies are synchronized
     */
    void synchronize(UUID communityId, LocalDate startDate, LocalDate endDate, String supplyCode);
}
