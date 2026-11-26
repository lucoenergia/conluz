package org.lucoenergia.conluz.domain.production.plant.surplusclaim;

import org.lucoenergia.conluz.domain.admin.community.energy.SurplusIncome;

/**
 * The totals of a plant's surplus claim over the month.
 *
 * @param surplusKWh       the sum of the month's hours, unrounded; null when no supply in scope stored a surplus
 *                         record in the month
 * @param realDays         the number of {@link SurplusClaimDayState#REAL} days
 * @param estimatedDays    the number of {@link SurplusClaimDayState#ESTIMATED} days
 * @param suppliesInScope  the number of supplies whose surplus the claim sums
 * @param suppliesWithData how many of them stored at least one surplus record in the month
 * @param surplusIncome    the estimated income of the month's hours; null when {@code surplusKWh} is null
 */
public record SurplusClaimSummary(Double surplusKWh, int realDays, int estimatedDays, int suppliesInScope,
                                  int suppliesWithData, SurplusIncome surplusIncome) {
}
