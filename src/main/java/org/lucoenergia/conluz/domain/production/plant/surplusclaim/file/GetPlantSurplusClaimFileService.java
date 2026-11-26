package org.lucoenergia.conluz.domain.production.plant.surplusclaim.file;

import java.time.YearMonth;
import java.util.UUID;

public interface GetPlantSurplusClaimFileService {

    /**
     * The surplus a plant claims from its retailer for one closed month, written in a retailer's format. The days,
     * their states and their hours are exactly those of the claim's preview for that month.
     *
     * @param month the closed month to claim; required, because the file must be the month the admin reviewed
     * @throws org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException when the plant does not exist
     * @throws org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimMonthNotClosedException when
     *                                         the month is the current one or later, in the zone of the plant's
     *                                         community
     * @throws SurplusClaimCilRequiredException when the format identifies the plant by its CIL and the plant has none
     */
    SurplusClaimFile getSurplusClaimFile(UUID plantId, YearMonth month, SurplusClaimFileType type);
}
