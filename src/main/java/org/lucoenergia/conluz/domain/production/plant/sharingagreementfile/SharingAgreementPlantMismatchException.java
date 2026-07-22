package org.lucoenergia.conluz.domain.production.plant.sharingagreementfile;

import java.util.UUID;

/**
 * Raised when a caller-supplied {@code sharingAgreementId} does not belong to the given plant.
 */
public class SharingAgreementPlantMismatchException extends RuntimeException {

    private final UUID sharingAgreementId;
    private final UUID plantId;

    public SharingAgreementPlantMismatchException(UUID sharingAgreementId, UUID plantId) {
        this.sharingAgreementId = sharingAgreementId;
        this.plantId = plantId;
    }

    public UUID getSharingAgreementId() {
        return sharingAgreementId;
    }

    public UUID getPlantId() {
        return plantId;
    }
}
