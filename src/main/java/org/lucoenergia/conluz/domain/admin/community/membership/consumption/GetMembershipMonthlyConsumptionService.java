package org.lucoenergia.conluz.domain.admin.community.membership.consumption;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface GetMembershipMonthlyConsumptionService {

    /**
     * The monthly consumption series of every supply the member owns in the community, one bucket
     * per local calendar month of the community's time zone whose day-1 midnight falls inside the
     * inclusive bounds, in chronological order, whether or not any supply stored a record that
     * month.
     *
     * @throws org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException when
     *         exactly one date is supplied or the start is after the end
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException when the
     *         user holds no membership in the community
     */
    List<MembershipMonthlyConsumptionBucket> getMonthlySeries(UUID communityId, UUID userId,
                                                              OffsetDateTime startDate, OffsetDateTime endDate);
}
