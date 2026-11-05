package org.lucoenergia.conluz.domain.admin.community.membership.consumption;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface GetMembershipHourlyConsumptionService {

    /**
     * The hourly consumption series of every supply the member owns in the community, one bucket per
     * whole hour that starts inside the half-open period, its end clipped to the start of the current
     * hour, in chronological order, whether or not any supply stored a record that hour. The hours
     * are counted on the instant line, so a day on which the clocks change has 23 or 25 of them. The
     * period may be at most {@code EnergyMetricsPeriodValidator.MAX_HOURLY_LENGTH} long.
     *
     * @throws org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException when the
     *         period breaks one of the period rules
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException when the
     *         user holds no membership in the community
     */
    List<MembershipHourlyConsumptionBucket> getHourlySeries(UUID communityId, UUID userId,
                                                            OffsetDateTime startDate, OffsetDateTime endDate);
}
