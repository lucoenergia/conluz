package org.lucoenergia.conluz.domain.consumption.datadis.sync;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.time.LocalDate;
import java.util.UUID;

public interface DatadisConsumptionSyncService {

    void synchronizeConsumptions(LocalDate startDate, LocalDate endDate);

    void synchronizeConsumptions(LocalDate startDate, LocalDate endDate, SupplyCode supplyCode);

    void synchronizeConsumptions(UUID communityId, LocalDate startDate, LocalDate endDate);
}
