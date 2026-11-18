package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.time.LocalDate;

/**
 * A valid local day of a consumer supply, with its observed β = E / G.
 */
record ValidDay(LocalDate day, double energyKWh, double productionKWh, double beta) {
}
