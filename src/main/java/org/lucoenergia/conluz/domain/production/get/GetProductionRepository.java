package org.lucoenergia.conluz.domain.production.get;

import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface GetProductionRepository {

    /**
     * Instant production restricted to the given InfluxDB {@code station_code}s (i.e. a community's plants).
     * An empty collection yields zero production.
     */
    InstantProduction getInstantProduction(Collection<String> stationCodes);

    List<ProductionByTime> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                             Float partitionCoefficient);

    /**
     * Hourly production for the given station codes, multiplied by {@code partitionCoefficient}.
     * An empty {@code stationCodes} collection yields an empty list.
     */
    List<ProductionByTime> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                             Float partitionCoefficient, Collection<String> stationCodes);

    List<ProductionByTime> getDailyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                             Float partitionCoefficient);

    List<ProductionByTime> getDailyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                            Float partitionCoefficient, Collection<String> stationCodes);

    List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                            Float partitionCoefficient);

    List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                            Float partitionCoefficient, Collection<String> stationCodes);

    List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                              Float partitionCoefficient, Collection<String> stationCodes);
}
