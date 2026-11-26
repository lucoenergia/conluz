package org.lucoenergia.conluz.domain.production.plant.surplusclaim;

import java.time.Instant;

/**
 * One hour of a plant's surplus claim.
 *
 * @param startsAt   the instant the hour starts at
 * @param surplusKWh the surplus of the supplies in scope summed over the hour, unrounded; null when none of them
 *                   stored a surplus record for the hour
 */
public record SurplusClaimHour(Instant startsAt, Double surplusKWh) {
}
