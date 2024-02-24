package org.lucoenergia.conluz.domain.consumption.datadis;

import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.time.Month;
import java.util.List;
import java.util.Map;

public interface DatadisConsumptionRepository {

    Map<String, List<Consumption>> getMonthlyConsumption(List<Supply> supplies, Month month, int year);
}
