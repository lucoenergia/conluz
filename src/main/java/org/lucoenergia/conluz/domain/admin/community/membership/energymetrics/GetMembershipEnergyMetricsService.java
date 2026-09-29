package org.lucoenergia.conluz.domain.admin.community.membership.energymetrics;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface GetMembershipEnergyMetricsService {

    /**
     * Adds up the energy metrics of every supply the member owns in the community over one period.
     *
     * <p>The period is resolved in one of three ways: the explicit {@code startDate} and
     * {@code endDate}, both inclusive; the {@code referencePeriod}; or, when neither is supplied,
     * from the earliest to the latest record stored for any of the member's supplies. Supplying a
     * reference period together with a date, exactly one date, or a start after the end raises
     * {@link org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException}.</p>
     *
     * @param referencePeriod the reference period to resolve, or null when none is requested
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException when the
     *         user holds no membership in the community
     */
    MembershipEnergyMetrics getEnergyMetrics(UUID communityId, UUID userId, OffsetDateTime startDate,
                                             OffsetDateTime endDate, EnergyMetricsReferencePeriod referencePeriod);
}
