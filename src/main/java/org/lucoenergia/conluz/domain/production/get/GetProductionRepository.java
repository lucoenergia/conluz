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

    /**
     * Raw (unscaled) hourly production for the given station codes, inclusive of both bounds.
     * An empty {@code stationCodes} collection yields an empty list.
     */
    List<ProductionByTime> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                             Collection<String> stationCodes);

    /**
     * Raw (unscaled) daily production sums, UTC-day-aligned, inclusive of both bounds. An empty
     * {@code stationCodes} collection yields an empty list.
     */
    List<ProductionByTime> getDailyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                            Collection<String> stationCodes);

    /**
     * Raw (unscaled) monthly production, read from the {@code huawei_production_kwh_month} pre-aggregate,
     * inclusive of both bounds. An empty {@code stationCodes} collection yields an empty list.
     */
    List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                            Collection<String> stationCodes);

    /**
     * Raw (unscaled) yearly production, read from the {@code huawei_production_kwh_year} pre-aggregate,
     * inclusive of both bounds. An empty {@code stationCodes} collection yields an empty list.
     */
    List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                              Collection<String> stationCodes);

    /**
     * Raw (unscaled) hourly points, HALF-OPEN {@code [from, to)}. Used only by the per-supply Hourly
     * granularity's single whole-range fetch per plant, so the fetched point set and the coefficient
     * segment coverage set are the same set of instants by construction. Callers must normalise their
     * (inclusive) API range to an exclusive {@code to} once, at the top of the request -- see
     * {@code GetProductionServiceImpl}.
     */
    List<ProductionByTime> getHourlyProductionHalfOpen(OffsetDateTime from, OffsetDateTime to, Collection<String> stationCodes);

    /**
     * Raw (unscaled) daily sums, UTC-day-aligned ({@code GROUP BY time(1d)}), HALF-OPEN {@code [from, to)}.
     * Used only by the per-supply Daily granularity, called once per coefficient segment.
     */
    List<ProductionByTime> getDailyProductionHalfOpen(OffsetDateTime from, OffsetDateTime to, Collection<String> stationCodes);

    /**
     * Raw (unscaled) daily sums, Europe/Madrid-calendar-day-aligned ({@code GROUP BY time(1d) tz('Europe/Madrid')}),
     * HALF-OPEN {@code [from, to)}. Used only by the per-supply Monthly/Yearly granularities, called
     * once per coefficient segment; each returned day is already an exact Madrid calendar day
     * (DST-correct: a 23-hour spring-forward day and a 25-hour fall-back day both resolve to exactly
     * one bucket of the right width) and is folded into its {@code YearMonth}/{@code Year} bucket in
     * Java by the caller.
     */
    List<ProductionByTime> getMadridAlignedDailyProductionHalfOpen(OffsetDateTime from, OffsetDateTime to, Collection<String> stationCodes);
}
