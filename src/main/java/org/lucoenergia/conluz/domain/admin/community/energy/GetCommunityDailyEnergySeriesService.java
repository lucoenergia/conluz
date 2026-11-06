package org.lucoenergia.conluz.domain.admin.community.energy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface GetCommunityDailyEnergySeriesService {

    /**
     * The daily energy series of the community over the explicit period, its end clipped to the start
     * of the current hour: one bucket for every local day of the community's time zone that overlaps
     * the period, in chronological order.
     *
     * @throws org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException when the
     *         requested period is invalid
     */
    List<CommunityEnergySeriesBucket> getDailySeries(UUID communityId, OffsetDateTime startDate,
                                                     OffsetDateTime endDate);
}
