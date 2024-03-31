package org.lucoenergia.conluz.domain.consumption.datadis.get;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;

import java.time.Month;
import java.util.List;

public interface GetDatadisConsumptionRepository {

    List<DatadisConsumption> getHourlyConsumptionsByMonth(@NotNull Supply supply, @NotNull Month month, @NotNull int year);
}
