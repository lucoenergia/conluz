package org.lucoenergia.conluz.domain.production.get;

import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;

import java.time.OffsetDateTime;
import java.util.List;

public interface GetProductionRepository {

    InstantProduction getInstantProduction();

    List<ProductionByTime> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);

    List<ProductionByTime> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                             Float partitionCoefficient);

    List<ProductionByTime> getDailyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);

    List<ProductionByTime> getDailyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                             Float partitionCoefficient);

    List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);

    List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                            Float partitionCoefficient);

    List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);

    List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                              Float partitionCoefficient);
}
