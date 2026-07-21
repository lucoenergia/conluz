package org.lucoenergia.conluz.domain.production.plant.sharingagreement;

import java.util.UUID;

public class SharingAgreementHasNoCoefficientsException extends RuntimeException {

    private final UUID id;

    public SharingAgreementHasNoCoefficientsException(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
