package org.lucoenergia.conluz.domain.shared.time;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Resolves the time zone a local date is converted through -- a coefficient's authored activation
 * date, a tariff segment's civil date range. Every method is parameterised by the entity the date
 * belongs to, even though today's implementation ignores the argument and returns an
 * application-level setting: the zone is expected to move to community level (both plants and
 * supplies belong to a community), and this single point is where that future change lands with
 * no call site touched. A caller with no community in reach asks for the application zone
 * explicitly, through {@link #resolveApplicationZoneId()}.
 *
 * <p>Consumers must obtain the zone here rather than by reading {@code TimeConfiguration}
 * directly, which is what keeps that future change confined to one class.
 */
public interface ZoneResolver {

    /**
     * The application-wide zone, for a caller with no community in reach -- a coefficient's
     * activation date or a Huawei pre-aggregate, which only know their plant. It is the zone every
     * other method returns today; it is not a plant's or a community's own zone.
     */
    ZoneId resolveApplicationZoneId();

    /**
     * The zone a supply point's local dates are interpreted in -- the civil dates of its tariff
     * {@code DateRange}, for instance. Parameterised by supply because a supply belongs to a
     * community, and the zone is expected to become a per-community setting, so this is the single
     * point that change lands with no call site touched.
     */
    ZoneId resolveZoneIdForSupply(UUID supplyId);

    /**
     * The zone a community's local dates are read in -- the civil start date of a payback, for
     * instance. Parameterised by community for the same reason the supply lookup is parameterised
     * by supply, and it is the level the zone is expected to settle at, since both plants
     * and supplies reach their community anyway.
     */
    ZoneId resolveZoneIdForCommunity(UUID communityId);
}
