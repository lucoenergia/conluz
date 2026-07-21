package org.lucoenergia.conluz.domain.production.plant.get;

import java.util.List;
import java.util.UUID;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;

/**
 * Service for retrieving sharing agreement information.
 */
public interface GetSharingAgreementService {

    /**
     * Sharing agreements of a plant, newest first. A {@code null} {@code status} means no filter.
     */
    List<SharingAgreement> findByPlantId(UUID plantId, SharingAgreementStatus status);

    /**
     * Find a sharing agreement by its ID.
     *
     * @throws org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotFoundException if no such agreement exists
     */
    SharingAgreement findById(UUID id);
}
