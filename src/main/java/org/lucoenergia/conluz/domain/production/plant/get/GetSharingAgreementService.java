package org.lucoenergia.conluz.domain.production.plant.get;

import org.lucoenergia.conluz.domain.production.plant.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.SharingAgreementStatus;

import java.util.List;
import java.util.UUID;

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
     * @throws org.lucoenergia.conluz.domain.production.plant.SharingAgreementNotFoundException if no such agreement exists
     */
    SharingAgreement findById(UUID id);
}
