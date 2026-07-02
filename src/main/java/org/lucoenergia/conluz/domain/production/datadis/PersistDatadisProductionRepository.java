package org.lucoenergia.conluz.domain.production.datadis;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface PersistDatadisProductionRepository {

    void persistHourlyProductions(@NotNull List<DatadisProduction> productions);
}
