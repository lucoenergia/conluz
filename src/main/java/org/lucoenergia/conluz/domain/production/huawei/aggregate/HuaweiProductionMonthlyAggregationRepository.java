package org.lucoenergia.conluz.domain.production.huawei.aggregate;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.production.plant.Plant;

import java.time.Month;

public interface HuaweiProductionMonthlyAggregationRepository {

    void aggregateMonthlyProduction(@NotNull Plant plant, @NotNull Month month, @NotNull int year);
}
