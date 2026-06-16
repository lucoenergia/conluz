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
     * Get hourly production by range of dates and supply.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param id the supply id
     * @return a list of hourly production for the supply
     */
    List<ProductionByTime> getHourlyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate, OffsetDateTime endDate, SupplyId id);

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
     * Get monthly production by range of dates and supply.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param id the supply id
     * @return a list of monthly production for the supply
     */
    List<ProductionByTime> getMonthlyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate, OffsetDateTime endDate, SupplyId id);


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