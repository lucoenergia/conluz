package org.lucoenergia.conluz.domain.consumption;

import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the reference month of a set of supplies: the most recent calendar month whose assigned
 * production Datadis has substantially published for any of them.
 *
 * <p>Datadis publishes a month's self-consumption only some days after the month ends, and until
 * then the month's records already carry consumption and the surplus the meter measured. A record
 * is therefore published when it carries self-consumption, zero included, and surplus alone proves
 * nothing. A month is published for a supply when at least {@link #PUBLISHED_HOURS_MIN_PERCENT} of
 * the hours it could have published carry it: the hours of the month, in the given zone, from the
 * later of the month's first hour and the supply's first published record. A supply whose assigned
 * production started mid-month is so judged against what could have been recorded for it.
 *
 * <p>The search goes backwards through the complete months before the current one, bounded by a
 * fixed window; the current month is never a candidate, since it is never published and would be a
 * partial period. When no month in the window is published for any supply, none is resolved.
 *
 * <p>A month published for <em>any</em> of the supplies qualifies, even if another one of them is
 * still unpublished. This is a watched assumption: Datadis publishes a distributor's month in one
 * batch, so a member's supplies normally publish together, and requiring every supply would leave a
 * member with a supply that never publishes -- a plant's own supply, or one that left the sharing --
 * without any month at all. Should a member's supplies stop publishing together, for example
 * because they belong to different distributors, the resolved month would carry the published
 * supply's assigned production alone.
 */
public interface ReferenceMonthResolver {

    /**
     * How many complete calendar months before the current one are searched.
     */
    int SEARCH_WINDOW_MONTHS = 24;

    /**
     * The minimum share, in percent, of a supply's possible hours in a month that must carry
     * published assigned production for the month to count as published for that supply.
     */
    int PUBLISHED_HOURS_MIN_PERCENT = 90;

    /**
     * The latest complete month, within the {@link #SEARCH_WINDOW_MONTHS search window}, that is
     * published for any of the supplies, with calendar months read in {@code zone}. Empty when no
     * month in the window is, including when there are no supplies.
     */
    Optional<YearMonth> resolveLatestPublishedMonth(List<Supply> supplies, ZoneId zone);
}
