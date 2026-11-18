package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.util.UUID;

/**
 * Observes how the distributor applies a plant's sharing agreements, from its daily Datadis data. Computed on
 * every read; nothing is stored (OBS-007).
 */
public interface GetSharingObservationService {

    /**
     * The observation of all the plant's PUBLISHED and SUPERSEDED agreements.
     */
    PlantSharingObservation observePlant(UUID plantId);

    /**
     * One agreement's slice of {@link #observePlant}.
     *
     * @throws SharingAgreementNotObservableException when the agreement is a DRAFT
     */
    SharingAgreementObservation observeAgreement(UUID plantId, UUID sharingAgreementId);
}
