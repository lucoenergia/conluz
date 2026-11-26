package org.lucoenergia.conluz.domain.production.plant.surplusclaim;

import java.time.LocalDate;
import java.util.List;

/**
 * One local day of a plant's surplus claim.
 *
 * @param date       the local day, in the zone of the plant's community
 * @param state      whether every supply in scope stored a record for every hour of the day
 * @param surplusKWh the sum of the day's hours, unrounded; null when every hour is null
 * @param peakHour   the earliest hour holding the day's largest surplus; null when the day's surplus is null or 0
 * @param hours      the 23, 24 or 25 hours of the day, in order
 */
public record SurplusClaimDay(LocalDate date, SurplusClaimDayState state, Double surplusKWh,
                              SurplusClaimHour peakHour, List<SurplusClaimHour> hours) {
}
