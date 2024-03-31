package org.lucoenergia.conluz.domain.consumption.datadis.persist;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;

import java.util.List;

public interface PersistDatadisConsumptionRepository {

    void persistConsumptions(@NotNull List<DatadisConsumption> consumptions);
}
