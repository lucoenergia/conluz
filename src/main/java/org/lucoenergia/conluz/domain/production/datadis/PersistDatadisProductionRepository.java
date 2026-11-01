package org.lucoenergia.conluz.domain.production.datadis;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;

import java.util.List;

public interface PersistDatadisProductionRepository {

    /**
     * Stores each production record at the instant it carries.
     */
    void persistHourlyProductions(@NotNull List<DatadisHourlyRecord<DatadisProduction>> productions);
}
