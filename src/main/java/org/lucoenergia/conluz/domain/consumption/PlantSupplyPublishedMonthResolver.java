package org.lucoenergia.conluz.domain.consumption;

import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Resolves the latest published month of a plant's own supply (ENM-009).
 *
 * <p>A plant's own supply never carries self-consumption, so {@link ReferenceMonthResolver} would never find a
 * month published for it. Its hours are those of its production records, and of its legacy surplus records on
 * the days before the first production record, never both for one day; a month is published for it under
 * ENM-001's threshold, window and current-month exclusion.</p>
 */
public interface PlantSupplyPublishedMonthResolver {

    /**
     * The latest complete month, within the {@link ReferenceMonthResolver#SEARCH_WINDOW_MONTHS search window},
     * that is published for the plant's own supply, with calendar months read in {@code zone}. Empty when none
     * is, including when the supply has no record in either source.
     */
    Optional<YearMonth> resolveLatestPublishedMonth(Supply plantSupply, ZoneId zone);
}
