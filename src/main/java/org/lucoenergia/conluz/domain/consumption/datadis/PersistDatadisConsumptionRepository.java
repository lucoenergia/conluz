package org.lucoenergia.conluz.domain.consumption.datadis;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface PersistDatadisConsumptionRepository {

    void persistConsumptions(@NotNull List<Consumption> consumptions);
}
