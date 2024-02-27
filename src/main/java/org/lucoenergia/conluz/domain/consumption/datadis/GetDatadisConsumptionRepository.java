package org.lucoenergia.conluz.domain.consumption.datadis;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.time.Month;
import java.util.List;

public interface GetDatadisConsumptionRepository {

    List<Consumption> getHourlyConsumptionsByMonth(@NotNull Supply supply, @NotNull Month month, @NotNull int year);
}
