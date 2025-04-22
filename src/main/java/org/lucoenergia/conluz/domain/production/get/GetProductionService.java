package org.lucoenergia.conluz.domain.production.get;

import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for retrieving production information.
 */
public interface GetProductionService {

    /**
     * Get instant production.
     *
     * @return the instant production
     */
    InstantProduction getInstantProduction();

    /**
     * Get instant production by supply.
     *
     * @param id the supply id
     * @return the instant production for the supply
     */
    InstantProduction getInstantProductionBySupply(SupplyId id);

    /**
     * Get hourly production by range of dates.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @return a list of hourly production
     */
    List<ProductionByTime> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Get hourly production by range of dates and supply.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param id the supply id
     * @return a list of hourly production for the supply
     */
    List<ProductionByTime> getHourlyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate, OffsetDateTime endDate, SupplyId id);

    /**
     * Get daily production by range of dates.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @return a list of daily production
     */
    List<ProductionByTime> getDailyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Get daily production by range of dates and supply.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param id the supply id
     * @return a list of daily production for the supply
     */
    List<ProductionByTime> getDailyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate, OffsetDateTime endDate, SupplyId id);

    /**
     * Get monthly production by range of dates.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @return a list of monthly production
     */
    List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Get monthly production by range of dates and supply.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param id the supply id
     * @return a list of monthly production for the supply
     */
    List<ProductionByTime> getMonthlyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate, OffsetDateTime endDate, SupplyId id);

    /**
     * Get yearly production by range of dates.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @return a list of yearly production
     */
    List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Get yearly production by range of dates and supply.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param id the supply id
     * @return a list of yearly production for the supply
     */
    List<ProductionByTime> getYearlyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate, OffsetDateTime endDate, SupplyId id);
}