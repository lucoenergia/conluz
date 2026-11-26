package org.lucoenergia.conluz.domain.production.plant.surplusclaim.file;

import org.lucoenergia.conluz.domain.production.plant.surplusclaim.PlantSurplusClaim;

/**
 * Writes a plant's surplus claim in one retailer's format. A new format is a new implementation.
 *
 * <p>A format only writes what the claim holds: the days, their states and their hours are the claim's own, and no
 * format computes any of them again.
 */
public interface SurplusClaimFileFormat {

    /**
     * The format this implementation writes.
     */
    SurplusClaimFileType type();

    /**
     * Writes the claim's days and hours.
     *
     * @throws SurplusClaimCilRequiredException when the format identifies the plant by its CIL and the plant has none
     */
    SurplusClaimFile write(PlantSurplusClaim claim);
}
