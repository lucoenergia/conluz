package org.lucoenergia.conluz.domain.production.plant.get;

import java.util.Optional;
import java.util.UUID;

public interface GetSharingAgreementRepository {

    /**
     * The id of the current PUBLISHED sharing agreement for a plant, if any.
     */
    Optional<UUID> findCurrentPublishedAgreementIdByPlantId(UUID plantId);
}
