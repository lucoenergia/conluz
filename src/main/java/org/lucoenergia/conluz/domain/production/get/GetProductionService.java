package org.lucoenergia.conluz.domain.production.get;

import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for retrieving production information.
 *
 * <p>Community-scoped methods restrict the time-series query to the plants of the given community
 * (resolved from PostgreSQL); the optional supply variants additionally apply the supply's
 * partition coefficient and require the supply to belong to that community.</p>
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

    // --- Community-scoped variants ---

    InstantProduction getInstantProductionByCommunity(UUID communityId);

    InstantProduction getInstantProductionByCommunityAndSupply(UUID communityId, SupplyId id);

    List<ProductionByTime> getHourlyProductionByRangeOfDatesAndCommunity(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                         UUID communityId);

    List<ProductionByTime> getHourlyProductionByRangeOfDatesAndCommunityAndSupply(OffsetDateTime startDate,
                                                                                  OffsetDateTime endDate,
                                                                                  UUID communityId, SupplyId id);

    List<ProductionByTime> getDailyProductionByRangeOfDatesAndCommunity(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                        UUID communityId);

    List<ProductionByTime> getDailyProductionByRangeOfDatesAndCommunityAndSupply(OffsetDateTime startDate,
                                                                                 OffsetDateTime endDate,
                                                                                 UUID communityId, SupplyId id);

    List<ProductionByTime> getMonthlyProductionByRangeOfDatesAndCommunity(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                          UUID communityId);

    List<ProductionByTime> getMonthlyProductionByRangeOfDatesAndCommunityAndSupply(OffsetDateTime startDate,
                                                                                   OffsetDateTime endDate,
                                                                                   UUID communityId, SupplyId id);

    List<ProductionByTime> getYearlyProductionByRangeOfDatesAndCommunity(OffsetDateTime startDate, OffsetDateTime endDate,
                                                                         UUID communityId);

    List<ProductionByTime> getYearlyProductionByRangeOfDatesAndCommunityAndSupply(OffsetDateTime startDate,
                                                                                  OffsetDateTime endDate,
                                                                                  UUID communityId, SupplyId id);
}