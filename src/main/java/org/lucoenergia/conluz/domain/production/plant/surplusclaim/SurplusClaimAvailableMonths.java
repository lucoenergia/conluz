package org.lucoenergia.conluz.domain.production.plant.surplusclaim;

import java.time.YearMonth;

/**
 * The range of months a plant's surplus can be claimed for.
 *
 * @param firstMonth           the month of the plant's earliest applied coefficient; null when it has none
 * @param latestPublishedMonth the latest month published for the supplies that ever held an applied coefficient of
 *                             the plant, the plant's own supply excluded; null when there is none
 */
public record SurplusClaimAvailableMonths(YearMonth firstMonth, YearMonth latestPublishedMonth) {
}
