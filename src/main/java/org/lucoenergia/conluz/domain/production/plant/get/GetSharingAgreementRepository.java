package org.lucoenergia.conluz.domain.production.plant.get;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;

public interface GetSharingAgreementRepository {

    /**
     * The id of the current PUBLISHED sharing agreement for a plant, if any.
     */
    Optional<UUID> findCurrentPublishedAgreementIdByPlantId(UUID plantId);

    Optional<SharingAgreement> findById(UUID id);

    /**
     * Sharing agreements of a plant, newest first. A {@code null} {@code status} means no filter.
     */
    List<SharingAgreement> findByPlantId(UUID plantId, SharingAgreementStatus status);
}
