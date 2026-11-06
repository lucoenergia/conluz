package org.lucoenergia.conluz.domain.admin.community.energy;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;

import java.time.OffsetDateTime;
import java.time.Period;
import java.util.UUID;

/**
 * Resolves the supplies and the single period the energy figures of a community are computed over,
 * so that every operation reporting on a community agrees on both.
 */
public interface CommunityEnergyScopeResolver {

    /**
     * Every enabled supply of the community and the period resolved for them.
     *
     * <p>The period is resolved in one of three ways: the explicit {@code startDate}, inclusive, and
     * {@code endDate}, exclusive, with an end after the start of the current hour clipped to it; the
     * {@code referencePeriod}; or, when neither is supplied, from the earliest to the latest record
     * stored for any of the supplies. An explicit period always resolves, even for a community without
     * supplies.</p>
     *
     * @param referencePeriod the reference period to resolve, or null when none is requested
     * @param maxLength       the longest explicit period the operation accepts
     * @throws org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException when the
     *         requested period breaks one of the rules of
     *         {@link org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator}
     */
    CommunityEnergyScope resolve(UUID communityId, OffsetDateTime startDate, OffsetDateTime endDate,
                                 EnergyMetricsReferencePeriod referencePeriod, Period maxLength);
}
