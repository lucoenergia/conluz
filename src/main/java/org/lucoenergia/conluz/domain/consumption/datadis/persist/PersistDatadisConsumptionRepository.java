package org.lucoenergia.conluz.domain.consumption.datadis.persist;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;

import java.util.List;

public interface PersistDatadisConsumptionRepository {

    /**
     * Stores each consumption record at the instant it carries.
     */
    void persistHourlyConsumptions(@NotNull List<DatadisHourlyRecord<DatadisConsumption>> consumptions);
}
