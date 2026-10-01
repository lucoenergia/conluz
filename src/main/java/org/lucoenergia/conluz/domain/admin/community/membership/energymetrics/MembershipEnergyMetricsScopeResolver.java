package org.lucoenergia.conluz.domain.admin.community.membership.energymetrics;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resolves the supplies and the single period the energy metrics of a membership are computed
 * over, so that every endpoint reporting on a membership agrees on both.
 */
public interface MembershipEnergyMetricsScopeResolver {

    /**
     * The member's supplies in the community and the period resolved for them.
     *
     * <p>The period is resolved in one of three ways: the explicit {@code startDate} and
     * {@code endDate}, both inclusive; the {@code referencePeriod}; or, when neither is supplied,
     * from the earliest to the latest record stored for any of the member's supplies. An explicit
     * period always resolves, even for a membership without supplies.</p>
     *
     * @param referencePeriod the reference period to resolve, or null when none is requested
     * @throws org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException when a
     *         reference period is requested together with a date, exactly one date is supplied, or
     *         the start is after the end
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException when the
     *         user holds no membership in the community
     */
    MembershipEnergyMetricsScope resolve(UUID communityId, UUID userId, OffsetDateTime startDate,
                                         OffsetDateTime endDate, EnergyMetricsReferencePeriod referencePeriod);

    /**
     * The member's supplies in the community and the
     * {@link EnergyMetricsReferencePeriod#LATEST_PUBLISHED_MONTH latest published month} resolved
     * for them, exactly as {@link #resolve} resolves that reference period.
     *
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException when the
     *         user holds no membership in the community
     */
    MembershipEnergyMetricsScope resolveLatestPublishedMonth(UUID communityId, UUID userId);
}
