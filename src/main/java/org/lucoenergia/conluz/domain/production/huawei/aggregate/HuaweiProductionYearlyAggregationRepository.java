package org.lucoenergia.conluz.domain.production.huawei.aggregate;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.production.plant.Plant;

public interface HuaweiProductionYearlyAggregationRepository {

    void aggregateYearlyProduction(@NotNull Plant plant, @NotNull int year);
}
