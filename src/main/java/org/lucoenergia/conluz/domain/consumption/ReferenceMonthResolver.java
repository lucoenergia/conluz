package org.lucoenergia.conluz.domain.consumption;

import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the reference month of a set of supplies: the most recent calendar month in which any
 * of them has stored assigned production.
 *
 * <p>Datadis publishes a month's self-consumption and surplus only some days after the month ends,
 * so the previous calendar month is not necessarily the latest one with figures. The search goes
 * backwards through the complete months before the current one, bounded by a fixed window; the
 * current month is never a candidate, since it is never published and would be a partial period.
 */
public interface ReferenceMonthResolver {

    /**
     * How many complete calendar months before the current one are searched.
     */
    int SEARCH_WINDOW_MONTHS = 24;

    /**
     * The latest complete month, within the {@link #SEARCH_WINDOW_MONTHS search window}, in which
     * any of the supplies stored assigned production, with calendar months read in {@code zone}.
     * Empty when none of them stored any in the window, including when there are no supplies.
     */
    Optional<YearMonth> resolveLatestPublishedMonth(List<Supply> supplies, ZoneId zone);
}
