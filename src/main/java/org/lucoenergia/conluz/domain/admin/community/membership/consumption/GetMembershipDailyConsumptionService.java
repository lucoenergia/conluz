package org.lucoenergia.conluz.domain.admin.community.membership.consumption;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface GetMembershipDailyConsumptionService {

    /**
     * The daily consumption series of every supply the member owns in the community, one bucket per
     * local calendar day of the community's time zone that overlaps the half-open period, its end
     * clipped to the start of the current hour, in chronological order, whether or not any supply
     * stored a record that day. The first and last day only cover the part of the day inside the
     * period.
     *
     * @throws org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException when the
     *         period breaks one of the period rules
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException when the
     *         user holds no membership in the community
     */
    List<MembershipDailyConsumptionBucket> getDailySeries(UUID communityId, UUID userId,
                                                          OffsetDateTime startDate, OffsetDateTime endDate);
}
