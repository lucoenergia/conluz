package org.lucoenergia.conluz.domain.production.sharingagreement.dates;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;

import java.util.Map;
import java.util.UUID;

/**
 * The effects of setting an agreement's coefficient dates, with the supplies and agreements they refer to.
 *
 * @param supplies   every supply the effects refer to, by id
 * @param agreements every agreement the effects refer to, by id, as it stood before the request
 */
public record CoefficientDatesOutcome(CoefficientDatesEffects effects, Map<UUID, Supply> supplies,
                                      Map<UUID, SharingAgreement> agreements) {

    public CoefficientDatesOutcome {
        supplies = Map.copyOf(supplies);
        agreements = Map.copyOf(agreements);
    }
}
