package org.lucoenergia.conluz.domain.production.plant.surplusclaim;

import java.time.YearMonth;
import java.util.UUID;

public interface GetPlantSurplusClaimService {

    /**
     * The surplus a plant would claim from its retailer for one closed month, hour by hour, with what is missing from
     * it.
     *
     * @param month the month to claim, or null for the latest month published for the plant's supplies, else the last
     *              closed month
     * @throws org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException when the plant does not exist
     * @throws SurplusClaimMonthNotClosedException    when the month is the current one or later, in the zone of the
     *                                                plant's community
     */
    PlantSurplusClaim getSurplusClaim(UUID plantId, YearMonth month);
}
