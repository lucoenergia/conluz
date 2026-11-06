package org.lucoenergia.conluz.domain.admin.community.energy;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface GetCommunityEnergyMetricsService {

    /**
     * The energy figures of the community over one period, resolved as
     * {@link CommunityEnergyScopeResolver#resolve} resolves it with the longest period most operations
     * accept.
     *
     * @param referencePeriod the reference period to resolve, or null when none is requested
     * @throws org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException when the
     *         requested period is invalid
     */
    CommunityEnergyMetrics getEnergyMetrics(UUID communityId, OffsetDateTime startDate, OffsetDateTime endDate,
                                            EnergyMetricsReferencePeriod referencePeriod);
}
